package com.tradecore.pipeline;

import com.tradecore.ledger.service.LedgerService;
import com.tradecore.order.service.OrderService;
import com.tradecore.risk.model.Order;
import com.tradecore.risk.model.RiskResult;
import com.tradecore.risk.service.RiskService;
import com.trading.engine.MatchingEngine;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * End-to-end integration pipeline coordinating Part D (Risk), Part A (Matching Engine),
 * Part B (Ledger & Settlement), and Part C (Notifications via Event Publisher).
 */
@Service
public class TradeCorePipelineService {

    private final OrderService orderService;
    private final RiskService riskService;
    private final MatchingEngine matchingEngine;
    private final LedgerService ledgerService;

    @org.springframework.beans.factory.annotation.Autowired
    public TradeCorePipelineService(OrderService orderService) {
        this.orderService = orderService;
        this.riskService = null;
        this.matchingEngine = null;
        this.ledgerService = null;
    }

    public TradeCorePipelineService(RiskService riskService,
                                   MatchingEngine matchingEngine,
                                   LedgerService ledgerService) {
        this.orderService = null;
        this.riskService = riskService;
        this.matchingEngine = matchingEngine;
        this.ledgerService = ledgerService;
    }

    /**
     * Submits an incoming order to the complete exchange processing pipeline.
     *
     * @param order the risk domain order
     * @return PipelineResult summarizing risk decision and settled trades
     */
    public PipelineResult processOrder(Order order) {
        if (orderService != null) {
            return orderService.processOrder(order);
        }

        // Direct pipeline execution for unit testing / standalone pipeline calls
        RiskResult riskResult = riskService.validate(order);
        if (!riskResult.accepted()) {
            return PipelineResult.rejected(riskResult);
        }

        com.trading.common.Order engineOrder = toEngineOrder(order);
        List<com.trading.common.Trade> executedTrades = matchingEngine.submit(engineOrder);

        List<com.tradecore.ledger.model.Trade> settledTrades = new ArrayList<>();
        for (com.trading.common.Trade engineTrade : executedTrades) {
            com.tradecore.ledger.model.Trade ledgerTrade = toLedgerTrade(engineTrade);
            boolean settled = ledgerService.settle(ledgerTrade);
            if (settled) {
                settledTrades.add(ledgerTrade);
            }
        }

        return PipelineResult.accepted(riskResult, executedTrades.size(), settledTrades);
    }

    private com.trading.common.Order toEngineOrder(Order order) {
        com.trading.common.Side engineSide = order.side() == com.tradecore.risk.model.Side.BUY
                ? com.trading.common.Side.BUY
                : com.trading.common.Side.SELL;

        return new com.trading.common.Order(
                order.id(),
                order.userId(),
                engineSide,
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

    public record PipelineResult(
            boolean accepted,
            RiskResult riskResult,
            int executedTradeCount,
            List<com.tradecore.ledger.model.Trade> settledTrades
    ) {
        public static PipelineResult rejected(RiskResult riskResult) {
            return new PipelineResult(false, riskResult, 0, List.of());
        }

        public static PipelineResult accepted(RiskResult riskResult, int executedTradeCount, List<com.tradecore.ledger.model.Trade> settledTrades) {
            return new PipelineResult(true, riskResult, executedTradeCount, settledTrades);
        }
    }
}
