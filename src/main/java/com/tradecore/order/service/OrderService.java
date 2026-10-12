package com.tradecore.order.service;

import com.tradecore.audit.AuditService;
import com.tradecore.ledger.service.LedgerService;
import com.tradecore.ledger.service.WalletService;
import com.tradecore.order.model.OrderEntity;
import com.tradecore.pipeline.TradeCorePipelineService.PipelineResult;
import com.tradecore.risk.model.Order;
import com.tradecore.risk.model.OrderType;
import com.tradecore.risk.model.RiskRejectReason;
import com.tradecore.risk.model.RiskResult;
import com.tradecore.risk.model.Side;
import com.tradecore.risk.service.RiskService;
import com.trading.engine.InstrumentMatchingEngineManager;
import com.trading.engine.MatchingEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final RiskService riskService;
    private final MatchingEngine matchingEngine;
    private final LedgerService ledgerService;
    private final WalletService walletService;
    private final AuditService auditService;
    private final JdbcTemplate jdbcTemplate;

    public OrderService(RiskService riskService,
                        MatchingEngine matchingEngine,
                        LedgerService ledgerService,
                        WalletService walletService,
                        AuditService auditService,
                        JdbcTemplate jdbcTemplate) {
        this.riskService = riskService;
        this.matchingEngine = matchingEngine;
        this.ledgerService = ledgerService;
        this.walletService = walletService;
        this.auditService = auditService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public PipelineResult processOrder(Order order) {
        // Step 1: Pre-trade risk validation
        RiskResult riskResult = riskService.validate(order);
        if (!riskResult.accepted()) {
            saveOrder(order, 0, "REJECTED");
            log.warn("Order {} rejected by pre-trade risk: {}", order.id(), riskResult.message());
            return PipelineResult.rejected(riskResult);
        }

        // Step 2: Reserve funds in user's wallet
        boolean reserved = walletService.reserveFunds(
                order.userId(), order.side(), order.price(), order.qty()
        );
        if (!reserved) {
            RiskResult fundsResult = RiskResult.reject(
                    RiskRejectReason.INSUFFICIENT_FUNDS,
                    "Insufficient available balance to place order (Required funds unavailable)"
            );
            saveOrder(order, 0, "REJECTED");
            return PipelineResult.rejected(fundsResult);
        }

        // Save initial OPEN order state
        saveOrder(order, 0, "OPEN");

        // Step 3: Convert to engine order & submit to matching engine for specified instrument symbol
        com.trading.common.Order engineOrder = toEngineOrder(order);
        List<com.trading.common.Trade> executedTrades;
        if (matchingEngine instanceof InstrumentMatchingEngineManager manager) {
            executedTrades = manager.submit(order.symbol(), engineOrder);
        } else {
            executedTrades = matchingEngine.submit(engineOrder);
        }

        // Step 4: Settle trades, record audit, update order status
        long totalFilledQty = 0;
        List<com.tradecore.ledger.model.Trade> settledTrades = new ArrayList<>();

        for (com.trading.common.Trade engineTrade : executedTrades) {
            com.tradecore.ledger.model.Trade ledgerTrade = toLedgerTrade(engineTrade);
            boolean settled = ledgerService.settle(ledgerTrade);
            if (settled) {
                settledTrades.add(ledgerTrade);
                totalFilledQty += engineTrade.qty();

                // Record match reason in Audit Trail
                String reason = String.format("Taker %s %s order #%s matched Maker order #%s for %s at price $%s (qty: %d, price-time priority)",
                        order.side(), order.orderType(), order.id(),
                        order.side() == Side.BUY ? engineTrade.sellOrderId() : engineTrade.buyOrderId(),
                        order.symbol(), engineTrade.price(), engineTrade.qty());
                auditService.recordMatchAudit(
                        engineTrade.tradeId(), engineTrade.buyOrderId(), engineTrade.sellOrderId(),
                        engineTrade.buyerId(), engineTrade.sellerId(), engineTrade.price(),
                        engineTrade.qty(), reason
                );

                // Update maker order filled quantity & status
                String makerOrderId = order.side() == Side.BUY ? engineTrade.sellOrderId() : engineTrade.buyOrderId();
                updateMakerOrderStatus(makerOrderId, engineTrade.qty());
            }
        }

        // Update taker order state
        String finalStatus = (totalFilledQty >= order.qty()) ? "FILLED" :
                (totalFilledQty > 0) ? "PARTIALLY_FILLED" :
                        (order.orderType() == OrderType.MARKET) ? "CANCELLED" : "OPEN";

        updateOrderStatus(order.id(), totalFilledQty, finalStatus);

        return PipelineResult.accepted(riskResult, executedTrades.size(), settledTrades);
    }

    @Transactional
    public boolean cancelOrder(String orderId, String userId) {
        OrderEntity orderEntity = getOrderById(orderId);
        if (orderEntity == null) {
            log.warn("Cannot cancel order {}: Order not found", orderId);
            return false;
        }

        if (!"OPEN".equalsIgnoreCase(orderEntity.status()) && !"PARTIALLY_FILLED".equalsIgnoreCase(orderEntity.status())) {
            log.warn("Cannot cancel order {}: Order status is {}", orderId, orderEntity.status());
            return false;
        }

        // 1. Remove resting order from Matching Engine
        boolean cancelledInEngine = matchingEngine.cancel(orderId);

        // 2. Unreserve remaining funds in user wallet
        long remainingQty = orderEntity.qty() - orderEntity.filledQty();
        if (remainingQty > 0) {
            walletService.unreserveFunds(orderEntity.userId(), orderEntity.side(), orderEntity.price(), remainingQty);
        }

        // 3. Update status to CANCELLED in DB
        updateOrderStatus(orderId, orderEntity.filledQty(), "CANCELLED");
        log.info("Successfully cancelled order {}", orderId);
        return true;
    }

    private void saveOrder(Order order, long filledQty, String status) {
        try {
            int updated = jdbcTemplate.update(
                    "UPDATE orders SET filled_qty = ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    filledQty, status, order.id()
            );
            if (updated == 0) {
                jdbcTemplate.update(
                        "INSERT INTO orders (id, symbol, user_id, side, order_type, price, qty, filled_qty, status, created_at, updated_at) " +
                                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                        order.id(), order.symbol(), order.userId(), order.side().name(),
                        order.orderType() != null ? order.orderType().name() : "LIMIT",
                        order.price(), order.qty(), filledQty, status
                );
            }
        } catch (Exception e) {
            log.error("Failed to save order {}: {}", order.id(), e.getMessage());
        }
    }

    private void updateOrderStatus(String orderId, long additionalFilledQty, String status) {
        try {
            jdbcTemplate.update(
                    "UPDATE orders SET filled_qty = filled_qty + ?, status = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    additionalFilledQty, status, orderId
            );
        } catch (Exception e) {
            log.error("Failed to update status for order {}: {}", orderId, e.getMessage());
        }
    }

    private void updateMakerOrderStatus(String makerOrderId, long fillQty) {
        try {
            jdbcTemplate.update(
                    "UPDATE orders SET filled_qty = filled_qty + ?, status = CASE WHEN (filled_qty + ?) >= qty THEN 'FILLED' ELSE 'PARTIALLY_FILLED' END, updated_at = CURRENT_TIMESTAMP WHERE id = ?",
                    fillQty, fillQty, makerOrderId
            );
        } catch (Exception e) {
            log.error("Failed to update maker order status for {}: {}", makerOrderId, e.getMessage());
        }
    }

    public OrderEntity getOrderById(String orderId) {
        List<OrderEntity> orders = jdbcTemplate.query(
                "SELECT id, symbol, user_id, side, order_type, price, qty, filled_qty, status, created_at, updated_at FROM orders WHERE id = ?",
                (rs, rowNum) -> new OrderEntity(
                        rs.getString("id"),
                        rs.getString("symbol"),
                        rs.getString("user_id"),
                        Side.valueOf(rs.getString("side")),
                        OrderType.valueOf(rs.getString("order_type")),
                        rs.getBigDecimal("price"),
                        rs.getLong("qty"),
                        rs.getLong("filled_qty"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                orderId
        );
        return orders.isEmpty() ? null : orders.get(0);
    }

    public List<OrderEntity> getUserOrders(String userId) {
        return jdbcTemplate.query(
                "SELECT id, symbol, user_id, side, order_type, price, qty, filled_qty, status, created_at, updated_at FROM orders WHERE user_id = ? ORDER BY created_at DESC LIMIT 50",
                (rs, rowNum) -> new OrderEntity(
                        rs.getString("id"),
                        rs.getString("symbol"),
                        rs.getString("user_id"),
                        Side.valueOf(rs.getString("side")),
                        OrderType.valueOf(rs.getString("order_type")),
                        rs.getBigDecimal("price"),
                        rs.getLong("qty"),
                        rs.getLong("filled_qty"),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("updated_at").toInstant()
                ),
                userId
        );
    }

    private com.trading.common.Order toEngineOrder(Order order) {
        com.trading.common.Side engineSide = order.side() == Side.BUY
                ? com.trading.common.Side.BUY
                : com.trading.common.Side.SELL;

        com.trading.common.OrderType engineType = order.orderType() == OrderType.MARKET
                ? com.trading.common.OrderType.MARKET
                : com.trading.common.OrderType.LIMIT;

        return new com.trading.common.Order(
                order.id(),
                order.symbol(),
                order.userId(),
                engineSide,
                engineType,
                order.price(),
                order.qty(),
                order.ts()
        );
    }

    private com.tradecore.ledger.model.Trade toLedgerTrade(com.trading.common.Trade trade) {
        return new com.tradecore.ledger.model.Trade(
                trade.tradeId(),
                trade.buyOrderId(),
                trade.sellOrderId(),
                trade.buyerId(),
                trade.sellerId(),
                trade.price(),
                trade.qty(),
                trade.ts()
        );
    }
}
