package com.tradecore.bot;

import com.tradecore.order.service.OrderService;
import com.tradecore.risk.model.Order;
import com.tradecore.risk.model.OrderType;
import com.tradecore.risk.model.Side;
import com.trading.engine.MatchingEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
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
    private final MatchingEngine matchingEngine;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong intervalMs = new AtomicLong(1000);
    private final Random random = new Random();

    private ScheduledExecutorService executor;
    private BigDecimal currentReferencePrice = new BigDecimal("50000.00");

    public BotMarketSimulationService(OrderService orderService, MatchingEngine matchingEngine) {
        this.orderService = orderService;
        this.matchingEngine = matchingEngine;
    }

    public synchronized void startBots() {
        if (running.compareAndSet(false, true)) {
            executor = Executors.newSingleThreadScheduledExecutor();
            executor.scheduleWithFixedDelay(this::tickBots, 500, intervalMs.get(), TimeUnit.MILLISECONDS);
            log.info("Started Market Simulation Bots with interval {}ms", intervalMs.get());
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
            int botChoice = random.nextInt(3);
            switch (botChoice) {
                case 0 -> runMarketMakerBot();
                case 1 -> runTrendFollowerBot();
                case 2 -> runNoiseBot();
            }
        } catch (Exception e) {
            log.error("Error during bot simulation tick: {}", e.getMessage());
        }
    }

    private void runMarketMakerBot() {
        // Market Maker places balanced Buy and Sell limit orders around current price
        BigDecimal spread = new BigDecimal(random.nextInt(20) + 5); // $5 to $25 spread
        BigDecimal buyPrice = currentReferencePrice.subtract(spread).setScale(2, RoundingMode.HALF_UP);
        BigDecimal sellPrice = currentReferencePrice.add(spread).setScale(2, RoundingMode.HALF_UP);
        long qty = random.nextInt(3) + 1;

        // Place MM BUY
        Order buyOrder = new Order(
                "bot-mm-b-" + UUID.randomUUID().toString().substring(0, 8),
                "bot_mm", Side.BUY, OrderType.LIMIT, buyPrice, qty, Instant.now()
        );
        orderService.processOrder(buyOrder);

        // Place MM SELL
        Order sellOrder = new Order(
                "bot-mm-s-" + UUID.randomUUID().toString().substring(0, 8),
                "bot_mm", Side.SELL, OrderType.LIMIT, sellPrice, qty, Instant.now()
        );
        orderService.processOrder(sellOrder);
    }

    private void runTrendFollowerBot() {
        // Trend bot occasionally moves current reference price slightly up or down
        double drift = (random.nextDouble() - 0.48) * 10; // slightly bullish bias
        currentReferencePrice = currentReferencePrice.add(BigDecimal.valueOf(drift)).setScale(2, RoundingMode.HALF_UP);
        if (currentReferencePrice.compareTo(new BigDecimal("10000.00")) < 0) {
            currentReferencePrice = new BigDecimal("50000.00");
        }

        Side side = drift > 0 ? Side.BUY : Side.SELL;
        long qty = random.nextInt(2) + 1;

        Order order = new Order(
                "bot-trend-" + UUID.randomUUID().toString().substring(0, 8),
                "bot_trend", side, OrderType.LIMIT, currentReferencePrice, qty, Instant.now()
        );
        orderService.processOrder(order);
    }

    private void runNoiseBot() {
        // Noise bot places small random limit or market orders
        boolean isMarket = random.nextBoolean();
        Side side = random.nextBoolean() ? Side.BUY : Side.SELL;
        long qty = 1;

        BigDecimal price = side == Side.BUY
                ? currentReferencePrice.add(new BigDecimal(random.nextInt(10)))
                : currentReferencePrice.subtract(new BigDecimal(random.nextInt(10)));

        Order order = new Order(
                "bot-noise-" + UUID.randomUUID().toString().substring(0, 8),
                "bot_noise", side, isMarket ? OrderType.MARKET : OrderType.LIMIT,
                price, qty, Instant.now()
        );
        orderService.processOrder(order);
    }
}
