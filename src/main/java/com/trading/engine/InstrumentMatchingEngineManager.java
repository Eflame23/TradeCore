package com.trading.engine;

import com.trading.common.Order;
import com.trading.common.OrderType;
import com.trading.common.Side;
import com.trading.common.Trade;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Primary
public class InstrumentMatchingEngineManager implements MatchingEngine {

    private final ConcurrentHashMap<String, OrderBookMatchingEngine> engines = new ConcurrentHashMap<>();

    public OrderBookMatchingEngine getEngine(String symbol) {
        String key = (symbol == null || symbol.isBlank()) ? "BTC-USD" : symbol.trim().toUpperCase();
        return engines.computeIfAbsent(key, k -> {
            OrderBookMatchingEngine engine = new OrderBookMatchingEngine();
            seedLiquidity(engine, k);
            return engine;
        });
    }

    private void seedLiquidity(OrderBookMatchingEngine engine, String symbol) {
        BigDecimal base = getReferencePrice(symbol);

        // Seed 3 resting Bids below base price
        for (int i = 1; i <= 3; i++) {
            BigDecimal price = base.subtract(BigDecimal.valueOf(i * 10.0)).setScale(2, RoundingMode.HALF_UP);
            engine.submit(new Order(
                    "seed-bid-" + symbol + "-" + i + "-" + UUID.randomUUID().toString().substring(0, 4),
                    symbol, "bot_mm", Side.BUY, OrderType.LIMIT, price, 2L, Instant.now()
            ));
        }

        // Seed 3 resting Asks above base price
        for (int i = 1; i <= 3; i++) {
            BigDecimal price = base.add(BigDecimal.valueOf(i * 10.0)).setScale(2, RoundingMode.HALF_UP);
            engine.submit(new Order(
                    "seed-ask-" + symbol + "-" + i + "-" + UUID.randomUUID().toString().substring(0, 4),
                    symbol, "bot_mm", Side.SELL, OrderType.LIMIT, price, 2L, Instant.now()
            ));
        }
    }

    private BigDecimal getReferencePrice(String symbol) {
        if ("AAPL".equalsIgnoreCase(symbol)) return new BigDecimal("185.50");
        if ("TSLA".equalsIgnoreCase(symbol)) return new BigDecimal("240.20");
        if ("NVDA".equalsIgnoreCase(symbol)) return new BigDecimal("125.40");
        if ("MSFT".equalsIgnoreCase(symbol)) return new BigDecimal("420.10");
        if ("GOOGL".equalsIgnoreCase(symbol)) return new BigDecimal("175.30");
        if ("GOLD".equalsIgnoreCase(symbol)) return new BigDecimal("2380.00");
        if ("SILVER".equalsIgnoreCase(symbol)) return new BigDecimal("30.50");
        if ("CRUDEOIL".equalsIgnoreCase(symbol)) return new BigDecimal("78.40");
        if ("ETH-USD".equalsIgnoreCase(symbol)) return new BigDecimal("3480.00");
        if ("SOL-USD".equalsIgnoreCase(symbol)) return new BigDecimal("152.00");
        return new BigDecimal("50250.00");
    }

    @Override
    public List<Trade> submit(Order order) {
        String symbol = order.symbol() != null ? order.symbol() : "BTC-USD";
        return getEngine(symbol).submit(order);
    }

    public List<Trade> submit(String symbol, Order order) {
        return getEngine(symbol).submit(order);
    }

    @Override
    public boolean cancel(String orderId) {
        for (OrderBookMatchingEngine engine : engines.values()) {
            if (engine.cancel(orderId)) {
                return true;
            }
        }
        return false;
    }

    public OrderBook.Snapshot snapshot(String symbol) {
        OrderBookMatchingEngine engine = getEngine(symbol);
        OrderBook.Snapshot snap = engine.snapshot();

        // If snapshot is empty, re-seed liquidity
        if (snap.bids().isEmpty() && snap.asks().isEmpty()) {
            seedLiquidity(engine, symbol);
            return engine.snapshot();
        }

        return snap;
    }
}
