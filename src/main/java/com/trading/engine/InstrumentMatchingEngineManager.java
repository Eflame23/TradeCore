package com.trading.engine;

import com.tradecore.instrument.Instrument;
import com.tradecore.instrument.InstrumentService;
import com.trading.common.Order;
import com.trading.common.OrderType;
import com.trading.common.Side;
import com.trading.common.Trade;
import org.springframework.context.annotation.Lazy;
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
    private final InstrumentService instrumentService;

    public InstrumentMatchingEngineManager(@Lazy InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    public OrderBookMatchingEngine getEngine(String symbol) {
        String key = (symbol == null || symbol.isBlank()) ? "BTC-USD" : symbol.trim().toUpperCase();
        return engines.computeIfAbsent(key, k -> {
            OrderBookMatchingEngine engine = new OrderBookMatchingEngine();
            seedLiquidity(engine, k);
            return engine;
        });
    }

    public void seedLiquidity(OrderBookMatchingEngine engine, String symbol) {
        BigDecimal base = getReferencePrice(symbol);
        BigDecimal step = getTickStep(base);

        // Seed 5 resting Bids below live base price
        for (int i = 1; i <= 5; i++) {
            BigDecimal price = base.subtract(step.multiply(BigDecimal.valueOf(i))).setScale(2, RoundingMode.HALF_UP);
            if (price.signum() > 0) {
                long qty = (i * 2L) + 1;
                engine.submit(new Order(
                        "seed-bid-" + symbol + "-" + i + "-" + UUID.randomUUID().toString().substring(0, 4),
                        symbol, "bot_mm", Side.BUY, OrderType.LIMIT, price, qty, Instant.now()
                ));
            }
        }

        // Seed 5 resting Asks above live base price
        for (int i = 1; i <= 5; i++) {
            BigDecimal price = base.add(step.multiply(BigDecimal.valueOf(i))).setScale(2, RoundingMode.HALF_UP);
            long qty = (i * 2L) + 1;
            engine.submit(new Order(
                    "seed-ask-" + symbol + "-" + i + "-" + UUID.randomUUID().toString().substring(0, 4),
                    symbol, "bot_mm", Side.SELL, OrderType.LIMIT, price, qty, Instant.now()
            ));
        }
    }

    private BigDecimal getTickStep(BigDecimal price) {
        if (price.compareTo(new BigDecimal("10000.00")) >= 0) return new BigDecimal("10.00");
        if (price.compareTo(new BigDecimal("1000.00")) >= 0) return new BigDecimal("1.00");
        if (price.compareTo(new BigDecimal("100.00")) >= 0) return new BigDecimal("0.50");
        if (price.compareTo(new BigDecimal("10.00")) >= 0) return new BigDecimal("0.10");
        return new BigDecimal("0.01");
    }

    private BigDecimal getReferencePrice(String symbol) {
        if (instrumentService != null) {
            Instrument inst = instrumentService.getInstrument(symbol);
            if (inst != null && inst.price() != null && inst.price().signum() > 0) {
                return inst.price();
            }
        }
        if ("AAPL".equalsIgnoreCase(symbol)) return new BigDecimal("185.50");
        if ("TSLA".equalsIgnoreCase(symbol)) return new BigDecimal("240.20");
        if ("NVDA".equalsIgnoreCase(symbol)) return new BigDecimal("125.40");
        if ("GOLD".equalsIgnoreCase(symbol)) return new BigDecimal("2380.00");
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

        // Dynamically maintain 5 Bids and 5 Asks around live price
        if (snap.bids().isEmpty() || snap.asks().isEmpty()) {
            seedLiquidity(engine, symbol);
            return engine.snapshot();
        }

        return snap;
    }
}
