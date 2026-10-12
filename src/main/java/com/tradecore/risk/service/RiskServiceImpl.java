package com.tradecore.risk.service;

import com.tradecore.risk.circuitbreaker.TradingCircuitBreaker;
import com.tradecore.risk.config.RiskProperties;
import com.tradecore.risk.model.Order;
import com.tradecore.risk.model.OrderType;
import com.tradecore.risk.model.RiskRejectReason;
import com.tradecore.risk.model.RiskResult;
import com.tradecore.risk.model.Side;
import com.tradecore.risk.position.PositionProvider;
import com.tradecore.risk.ratelimit.RateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Implementation of RiskService providing comprehensive pre-trade validation.
 */
@Service
public class RiskServiceImpl implements RiskService {

    private static final Logger log = LoggerFactory.getLogger(RiskServiceImpl.class);

    private final RiskProperties properties;
    private final RateLimiter rateLimiter;
    private final TradingCircuitBreaker circuitBreaker;
    private final PositionProvider positionProvider;

    private final ConcurrentHashMap<String, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    public RiskServiceImpl(RiskProperties properties,
                           RateLimiter rateLimiter,
                           TradingCircuitBreaker circuitBreaker,
                           PositionProvider positionProvider) {
        this.properties = properties;
        this.rateLimiter = rateLimiter;
        this.circuitBreaker = circuitBreaker;
        this.positionProvider = positionProvider;
    }

    @Override
    public RiskResult validate(Order order) {
        // 1. Global trading halt check
        if (circuitBreaker.isHalted()) {
            return reject(RiskRejectReason.TRADING_HALTED, "Trading is currently halted by administrative or circuit breaker action");
        }

        // 2. Fundamental Order validation
        if (order == null) {
            return reject(RiskRejectReason.ORDER_NULL, "Order cannot be null");
        }

        if (order.id() == null || order.id().isBlank()) {
            return reject(RiskRejectReason.ORDER_ID_INVALID, "Order ID cannot be null or blank");
        }

        if (order.userId() == null || order.userId().isBlank()) {
            return reject(RiskRejectReason.USER_ID_INVALID, "User ID cannot be null or blank");
        }

        if (order.side() == null) {
            return reject(RiskRejectReason.SIDE_INVALID, "Order side must be specified (BUY or SELL)");
        }

        if (order.qty() <= 0) {
            return reject(RiskRejectReason.INVALID_QUANTITY, "Order quantity must be strictly positive. Provided: " + order.qty());
        }

        OrderType orderType = order.orderType() != null ? order.orderType() : OrderType.LIMIT;

        if (orderType == OrderType.LIMIT) {
            if (order.price() == null || order.price().compareTo(BigDecimal.ZERO) <= 0) {
                return reject(RiskRejectReason.INVALID_PRICE, "LIMIT order price must be strictly positive. Provided: " + order.price());
            }
        }

        // 3. Per-user Rate limiting
        if (!rateLimiter.tryAcquire(order.userId())) {
            return reject(RiskRejectReason.RATE_LIMIT_EXCEEDED,
                    String.format("Rate limit exceeded for user '%s'. Maximum %d requests per %d seconds allowed.",
                            order.userId(), properties.getRateLimitCapacity(), properties.getRateLimitWindowSeconds()));
        }

        // 4. Maximum Order Quantity check
        if (order.qty() > properties.getMaxOrderQuantity()) {
            return reject(RiskRejectReason.MAX_QUANTITY_EXCEEDED,
                    String.format("Order quantity %d exceeds maximum allowable single order quantity %d",
                            order.qty(), properties.getMaxOrderQuantity()));
        }

        // 5. Position Limits
        RiskResult positionResult = validatePositionLimit(order);
        if (!positionResult.accepted()) {
            return positionResult;
        }

        // Accepted: report success to circuit breaker and return OK
        circuitBreaker.recordSuccess();
        return RiskResult.accept();
    }

    private RiskResult validatePositionLimit(Order order) {
        ReentrantLock lock = userLocks.computeIfAbsent(order.userId(), k -> new ReentrantLock());
        lock.lock();
        try {
            Optional<BigDecimal> positionOpt = positionProvider.getPosition(order.userId());
            if (positionOpt.isEmpty()) {
                log.warn("Position data unavailable for user '{}'. Rejecting order to ensure safety.", order.userId());
                return reject(RiskRejectReason.POSITION_DATA_UNAVAILABLE,
                        String.format("Position data for user '%s' is unavailable. Order rejected for safety.", order.userId()));
            }

            BigDecimal currentPosition = positionOpt.get();
            BigDecimal orderQty = BigDecimal.valueOf(order.qty());
            BigDecimal resultingPosition;

            if (order.side() == Side.BUY) {
                resultingPosition = currentPosition.add(orderQty);
            } else {
                resultingPosition = currentPosition.subtract(orderQty);
            }

            BigDecimal maxPos = properties.getMaxAbsolutePosition();
            if (resultingPosition.abs().compareTo(maxPos) > 0) {
                return reject(RiskRejectReason.POSITION_LIMIT_EXCEEDED,
                        String.format("Projected position %s exceeds maximum absolute position limit %s (Current: %s, Order Side: %s, Order Qty: %d)",
                                resultingPosition, maxPos, currentPosition, order.side(), order.qty()));
            }

            return RiskResult.accept();
        } finally {
            lock.unlock();
        }
    }

    private RiskResult reject(RiskRejectReason reason, String message) {
        circuitBreaker.recordRejection();
        log.warn("Risk check rejected: [{}] {}", reason, message);
        return RiskResult.reject(reason, message);
    }
}
