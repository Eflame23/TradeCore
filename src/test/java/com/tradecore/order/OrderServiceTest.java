package com.tradecore.order;

import com.tradecore.order.service.OrderService;
import com.tradecore.pipeline.TradeCorePipelineService.PipelineResult;
import com.tradecore.risk.model.Order;
import com.tradecore.risk.model.OrderType;
import com.tradecore.risk.model.Side;
import com.trading.common.Trade;
import com.trading.engine.OrderBook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Order Service & Engine Core Features Unit Tests")
class OrderServiceTest {

    @Test
    @DisplayName("Should execute Market BUY order against resting Ask")
    void testMarketOrderExecution() {
        OrderBook book = new OrderBook();

        // Resting Ask: SELL 2 BTC @ $50,000.00
        com.trading.common.Order restingAsk = new com.trading.common.Order(
                "ask-1", "bob", com.trading.common.Side.SELL,
                com.trading.common.OrderType.LIMIT, new BigDecimal("50000.00"), 2L, Instant.now()
        );
        book.match(restingAsk);

        // Incoming Market BUY order: 1 BTC
        com.trading.common.Order marketBuy = new com.trading.common.Order(
                "buy-mkt-1", "alice", com.trading.common.Side.BUY,
                com.trading.common.OrderType.MARKET, null, 1L, Instant.now()
        );
        List<Trade> trades = book.match(marketBuy);

        assertEquals(1, trades.size());
        assertEquals(new BigDecimal("50000.00"), trades.get(0).price());
        assertEquals(1L, trades.get(0).qty());
    }

    @Test
    @DisplayName("Self-Trade Prevention should cancel resting maker order when matching same user")
    void testSelfTradePrevention() {
        OrderBook book = new OrderBook();

        // Alice rests SELL order
        com.trading.common.Order ask = new com.trading.common.Order(
                "ask-alice", "alice", com.trading.common.Side.SELL,
                com.trading.common.OrderType.LIMIT, new BigDecimal("50000.00"), 1L, Instant.now()
        );
        book.match(ask);

        // Alice submits BUY order
        com.trading.common.Order buy = new com.trading.common.Order(
                "buy-alice", "alice", com.trading.common.Side.BUY,
                com.trading.common.OrderType.LIMIT, new BigDecimal("50000.00"), 1L, Instant.now()
        );
        List<Trade> trades = book.match(buy);

        // Self-trade prevented: 0 trades executed
        assertTrue(trades.isEmpty());
    }
}
