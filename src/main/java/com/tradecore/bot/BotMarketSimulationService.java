package com.tradecore.bot;

import com.tradecore.order.service.OrderService;
import com.tradecore.risk.model.Order;
import com.tradecore.risk.model.OrderType;
import com.tradecore.risk.model.Side;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class BotMarketSimulationService {

    private static final Logger log = LoggerFactory.getLogger(BotMarketSimulationService.class);

    private final OrderService orderService;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong intervalMs = new AtomicLong(1000);
    private final Random random = new Random();

    private final List<String> activeSymbols = List.of(
            "BTC-USD", "ETH-USD", "SOL-USD", "AAPL", "NVDA", "TSLA", "GOLD", "CRUDEOIL"
    );

    private ScheduledExecutorService executor;

    public BotMarketSimulationService(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostConstruct
    public void init() {
        startBots();
    }

    public synchronized void startBots() {
        if (running.compareAndSet(false, true)) {
            executor = Executors.newSingleThreadScheduledExecutor();
            executor.scheduleWithFixedDelay(this::tickBots, 500, intervalMs.get(), TimeUnit.MILLISECONDS);
            log.info("Started Multi-Asset Market Simulation Bots with interval {}ms", intervalMs.get());
        }
    }

    public synchronized void stopBots() {
        if (running.compareAndSet(true, false)) {
            if (executor != null) {
                executor.shutdownNow();
            }
            log.info("Stopped Market Simulation Bots");
        }
    }

    public void setIntervalMs(long ms) {
        if (ms < 100) ms = 100;
        this.intervalMs.set(ms);
        if (running.get()) {
            stopBots();
            startBots();
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    public long getIntervalMs() {
        return intervalMs.get();
    }

    private void tickBots() {
        if (!running.get()) return;

        try {
            String symbol = activeSymbols.get(random.nextInt(activeSymbols.size()));
            BigDecimal referencePrice = getReferencePrice(symbol);

            int botChoice = random.nextInt(3);
            switch (botChoice) {
                case 0 -> runMarketMakerBot(symbol, referencePrice);
                case 1 -> runTrendFollowerBot(symbol, referencePrice);
                case 2 -> runNoiseBot(symbol, referencePrice);
            }
        } catch (Exception e) {
            log.error("Error during bot simulation tick: {}", e.getMessage());
        }
    }

    private BigDecimal getReferencePrice(String symbol) {
        if ("AAPL".equalsIgnoreCase(symbol)) return new BigDecimal("185.50");
        if ("NVDA".equalsIgnoreCase(symbol)) return new BigDecimal("125.40");
        if ("TSLA".equalsIgnoreCase(symbol)) return new BigDecimal("240.20");
        if ("GOLD".equalsIgnoreCase(symbol)) return new BigDecimal("2380.00");
        if ("CRUDEOIL".equalsIgnoreCase(symbol)) return new BigDecimal("78.40");
        if ("ETH-USD".equalsIgnoreCase(symbol)) return new BigDecimal("3480.00");
        if ("SOL-USD".equalsIgnoreCase(symbol)) return new BigDecimal("152.00");
        return new BigDecimal("50250.00");
    }

    private void runMarketMakerBot(String symbol, BigDecimal refPrice) {
        BigDecimal step = refPrice.multiply(new BigDecimal("0.001")).setScale(2, RoundingMode.HALF_UP);
        BigDecimal buyPrice = refPrice.subtract(step).setScale(2, RoundingMode.HALF_UP);
        BigDecimal sellPrice = refPrice.add(step).setScale(2, RoundingMode.HALF_UP);
        long qty = random.nextInt(2) + 1;

        Order buyOrder = new Order(
                "bot-mm-b-" + UUID.randomUUID().toString().substring(0, 8),
                symbol, "bot_mm", Side.BUY, OrderType.LIMIT, buyPrice, qty, Instant.now()
        );
        orderService.processOrder(buyOrder);

        Order sellOrder = new Order(
                "bot-mm-s-" + UUID.randomUUID().toString().substring(0, 8),
                symbol, "bot_mm", Side.SELL, OrderType.LIMIT, sellPrice, qty, Instant.now()
        );
        orderService.processOrder(sellOrder);
    }

    private void runTrendFollowerBot(String symbol, BigDecimal refPrice) {
        double driftPercent = (random.nextDouble() - 0.49) * 0.004;
        BigDecimal price = refPrice.multiply(BigDecimal.valueOf(1 + driftPercent)).setScale(2, RoundingMode.HALF_UP);
        Side side = driftPercent > 0 ? Side.BUY : Side.SELL;
        long qty = random.nextInt(2) + 1;

        Order order = new Order(
                "bot-trend-" + UUID.randomUUID().toString().substring(0, 8),
                symbol, "bot_trend", side, OrderType.LIMIT, price, qty, Instant.now()
        );
        orderService.processOrder(order);
    }

    private void runNoiseBot(String symbol, BigDecimal refPrice) {
        boolean isMarket = random.nextBoolean();
        Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
        long qty = 1;

        BigDecimal offset = refPrice.multiply(BigDecimal.valueOf((random.nextDouble() - 0.5) * 0.002)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal price = refPrice.add(offset);

        Order order = new Order(
                "bot-noise-" + UUID.randomUUID().toString().substring(0, 8),
                symbol, "bot_noise", side, isMarket ? OrderType.MARKET : OrderType.LIMIT,
                price, qty, Instant.now()
        );
        orderService.processOrder(order);
    }
}
