package com.tradecore.risk.service;

import com.tradecore.risk.circuitbreaker.TradingCircuitBreaker;
import com.tradecore.risk.config.RiskProperties;
import com.tradecore.risk.model.Order;
import com.tradecore.risk.model.RiskRejectReason;
import com.tradecore.risk.model.RiskResult;
import com.tradecore.risk.model.Side;
import com.tradecore.risk.position.PositionProvider;
import com.tradecore.risk.ratelimit.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RiskService Unit Tests")
class RiskServiceTest {

    @Mock
    private RateLimiter rateLimiter;

    @Mock
    private PositionProvider positionProvider;

    private RiskProperties properties;
    private TradingCircuitBreaker circuitBreaker;
    private RiskServiceImpl riskService;

    @BeforeEach
    void setUp() {
        properties = new RiskProperties();
        properties.setMaxOrderQuantity(10L); // Max 10 BTC
        properties.setMaxAbsolutePosition(new BigDecimal("100")); // Max 100 BTC
        properties.setRateLimitCapacity(20);

        circuitBreaker = new TradingCircuitBreaker(properties);
        riskService = new RiskServiceImpl(properties, rateLimiter, circuitBreaker, positionProvider);
    }

    private Order createValidOrder() {
        return new Order("ord-1", "alice", Side.BUY, new BigDecimal("50000"), 2L, Instant.now());
    }

    @Test
    @DisplayName("1. Valid order is accepted")
    void shouldAcceptValidOrder() {
        when(rateLimiter.tryAcquire("alice")).thenReturn(true);
        when(positionProvider.getPosition("alice")).thenReturn(Optional.of(new BigDecimal("10")));

        Order order = createValidOrder();
        RiskResult result = riskService.validate(order);

        assertTrue(result.accepted());
        assertNull(result.rejectReason());
    }

    @Test
    @DisplayName("2. Null or malformed order is rejected")
    void shouldRejectNullOrMalformedOrder() {
        RiskResult nullResult = riskService.validate(null);
        assertFalse(nullResult.accepted());
        assertEquals(RiskRejectReason.ORDER_NULL, nullResult.rejectReason());

        RiskResult missingIdResult = riskService.validate(new Order("", "alice", Side.BUY, new BigDecimal("100"), 1L, Instant.now()));
        assertFalse(missingIdResult.accepted());
        assertEquals(RiskRejectReason.ORDER_ID_INVALID, missingIdResult.rejectReason());

        RiskResult missingUserResult = riskService.validate(new Order("ord-1", "  ", Side.BUY, new BigDecimal("100"), 1L, Instant.now()));
        assertFalse(missingUserResult.accepted());
        assertEquals(RiskRejectReason.USER_ID_INVALID, missingUserResult.rejectReason());

        RiskResult missingSideResult = riskService.validate(new Order("ord-1", "alice", null, new BigDecimal("100"), 1L, Instant.now()));
        assertFalse(missingSideResult.accepted());
        assertEquals(RiskRejectReason.SIDE_INVALID, missingSideResult.rejectReason());
    }

    @Test
    @DisplayName("3. Non-positive price is rejected")
    void shouldRejectNonPositivePrice() {
        Order zeroPrice = new Order("ord-1", "alice", Side.BUY, BigDecimal.ZERO, 1L, Instant.now());
        RiskResult zeroResult = riskService.validate(zeroPrice);
        assertFalse(zeroResult.accepted());
        assertEquals(RiskRejectReason.INVALID_PRICE, zeroResult.rejectReason());

        Order negativePrice = new Order("ord-1", "alice", Side.BUY, new BigDecimal("-100"), 1L, Instant.now());
        RiskResult negResult = riskService.validate(negativePrice);
        assertFalse(negResult.accepted());
        assertEquals(RiskRejectReason.INVALID_PRICE, negResult.rejectReason());
    }

    @Test
    @DisplayName("4. Non-positive quantity is rejected")
    void shouldRejectNonPositiveQuantity() {
        Order zeroQty = new Order("ord-1", "alice", Side.BUY, new BigDecimal("100"), 0L, Instant.now());
        RiskResult zeroResult = riskService.validate(zeroQty);
        assertFalse(zeroResult.accepted());
        assertEquals(RiskRejectReason.INVALID_QUANTITY, zeroResult.rejectReason());

        Order negativeQty = new Order("ord-1", "alice", Side.BUY, new BigDecimal("100"), -5L, Instant.now());
        RiskResult negResult = riskService.validate(negativeQty);
        assertFalse(negResult.accepted());
        assertEquals(RiskRejectReason.INVALID_QUANTITY, negResult.rejectReason());
    }

    @Test
    @DisplayName("5. Quantity exceeding the maximum is rejected")
    void shouldRejectQuantityExceedingMax() {
        when(rateLimiter.tryAcquire("alice")).thenReturn(true);

        Order largeOrder = new Order("ord-1", "alice", Side.BUY, new BigDecimal("100"), 15L, Instant.now());
        RiskResult result = riskService.validate(largeOrder);

        assertFalse(result.accepted());
        assertEquals(RiskRejectReason.MAX_QUANTITY_EXCEEDED, result.rejectReason());
        assertTrue(result.message().contains("exceeds maximum allowable"));
    }

    @Test
    @DisplayName("6. BUY order exceeding positive position limit is rejected")
    void shouldRejectBuyExceedingPositionLimit() {
        when(rateLimiter.tryAcquire("alice")).thenReturn(true);
        // Current position 95 BTC + Buy 8 BTC = 103 BTC > 100 BTC
        when(positionProvider.getPosition("alice")).thenReturn(Optional.of(new BigDecimal("95")));

        Order buyOrder = new Order("ord-1", "alice", Side.BUY, new BigDecimal("100"), 8L, Instant.now());
        RiskResult result = riskService.validate(buyOrder);

        assertFalse(result.accepted());
        assertEquals(RiskRejectReason.POSITION_LIMIT_EXCEEDED, result.rejectReason());
        assertTrue(result.message().contains("Projected position 103"));
    }

    @Test
    @DisplayName("7. SELL order exceeding negative position limit is rejected")
    void shouldRejectSellExceedingNegativePositionLimit() {
        when(rateLimiter.tryAcquire("bob")).thenReturn(true);
        // Current position -95 BTC - Sell 8 BTC = -103 BTC (abs 103 > 100)
        when(positionProvider.getPosition("bob")).thenReturn(Optional.of(new BigDecimal("-95")));

        Order sellOrder = new Order("ord-2", "bob", Side.SELL, new BigDecimal("100"), 8L, Instant.now());
        RiskResult result = riskService.validate(sellOrder);

        assertFalse(result.accepted());
        assertEquals(RiskRejectReason.POSITION_LIMIT_EXCEEDED, result.rejectReason());
        assertTrue(result.message().contains("Projected position -103"));
    }

    @Test
    @DisplayName("10. Requests beyond rate limit are rejected")
    void shouldRejectOrdersWhenRateLimitExceeded() {
        when(rateLimiter.tryAcquire("alice")).thenReturn(false);

        Order order = createValidOrder();
        RiskResult result = riskService.validate(order);

        assertFalse(result.accepted());
        assertEquals(RiskRejectReason.RATE_LIMIT_EXCEEDED, result.rejectReason());
        verify(positionProvider, never()).getPosition(anyString());
    }

    @Test
    @DisplayName("13. Trading halt rejects new orders")
    void shouldRejectOrdersWhenTradingIsHalted() {
        circuitBreaker.haltTrading("Emergency halt");

        Order order = createValidOrder();
        RiskResult result = riskService.validate(order);

        assertFalse(result.accepted());
        assertEquals(RiskRejectReason.TRADING_HALTED, result.rejectReason());
        verify(rateLimiter, never()).tryAcquire(anyString());
    }

    @Test
    @DisplayName("14. Resuming trading permits eligible orders again")
    void shouldPermitOrdersAfterTradingResumed() {
        circuitBreaker.haltTrading("Temporary pause");
        assertFalse(riskService.validate(createValidOrder()).accepted());

        circuitBreaker.resumeTrading();
        when(rateLimiter.tryAcquire("alice")).thenReturn(true);
        when(positionProvider.getPosition("alice")).thenReturn(Optional.of(BigDecimal.ZERO));

        RiskResult result = riskService.validate(createValidOrder());
        assertTrue(result.accepted());
    }

    @Test
    @DisplayName("17. Position validation fails safely when position data is unavailable")
    void shouldFailSafelyWhenPositionDataUnavailable() {
        when(rateLimiter.tryAcquire("alice")).thenReturn(true);
        when(positionProvider.getPosition("alice")).thenReturn(Optional.empty());

        Order order = createValidOrder();
        RiskResult result = riskService.validate(order);

        assertFalse(result.accepted());
        assertEquals(RiskRejectReason.POSITION_DATA_UNAVAILABLE, result.rejectReason());
        assertTrue(result.message().contains("unavailable"));
    }
}
