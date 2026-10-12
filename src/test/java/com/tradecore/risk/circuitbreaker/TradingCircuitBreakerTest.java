package com.tradecore.risk.circuitbreaker;

import com.tradecore.risk.config.RiskProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TradingCircuitBreaker Tests")
class TradingCircuitBreakerTest {

    private RiskProperties properties;
    private TradingCircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        properties = new RiskProperties();
        circuitBreaker = new TradingCircuitBreaker(properties);
    }

    @Test
    @DisplayName("Trading should initially be active")
    void shouldBeActiveInitially() {
        assertFalse(circuitBreaker.isHalted());
        assertEquals(0, circuitBreaker.getConsecutiveRejectionCount());
    }

    @Test
    @DisplayName("Manual halt should set status to halted")
    void shouldHaltManually() {
        circuitBreaker.haltTrading("Market anomaly detected");
        assertTrue(circuitBreaker.isHalted());
    }

    @Test
    @DisplayName("Resuming trading should permit orders again")
    void shouldResumeTrading() {
        circuitBreaker.haltTrading("Emergency");
        assertTrue(circuitBreaker.isHalted());

        circuitBreaker.resumeTrading();
        assertFalse(circuitBreaker.isHalted());
        assertEquals(0, circuitBreaker.getConsecutiveRejectionCount());
    }

    @Test
    @DisplayName("Automatic tripping on consecutive risk rejections when enabled")
    void shouldTripAutomaticallyOnConsecutiveRejections() {
        properties.getCircuitBreaker().setAutoTripEnabled(true);
        properties.getCircuitBreaker().setConsecutiveRejectionThreshold(3);

        circuitBreaker.recordRejection();
        circuitBreaker.recordRejection();
        assertFalse(circuitBreaker.isHalted(), "Should not halt before threshold");

        circuitBreaker.recordRejection(); // 3rd rejection
        assertTrue(circuitBreaker.isHalted(), "Circuit breaker should trip on reaching threshold");

        // Further rejections keep it halted
        circuitBreaker.recordRejection();
        assertTrue(circuitBreaker.isHalted());

        // Requires explicit resume
        circuitBreaker.resumeTrading();
        assertFalse(circuitBreaker.isHalted());
    }

    @Test
    @DisplayName("Recording success should reset consecutive rejection counter")
    void shouldResetConsecutiveRejectionsOnSuccess() {
        properties.getCircuitBreaker().setAutoTripEnabled(true);
        properties.getCircuitBreaker().setConsecutiveRejectionThreshold(3);

        circuitBreaker.recordRejection();
        circuitBreaker.recordRejection();
        assertEquals(2, circuitBreaker.getConsecutiveRejectionCount());

        circuitBreaker.recordSuccess();
        assertEquals(0, circuitBreaker.getConsecutiveRejectionCount());
        assertFalse(circuitBreaker.isHalted());
    }

    @Test
    @DisplayName("Concurrent access to circuit breaker should be thread-safe")
    void shouldBeThreadSafeUnderConcurrentAccess() throws InterruptedException {
        int threads = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch latch = new CountDownLatch(threads);
        AtomicInteger halts = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    if (index % 2 == 0) {
                        circuitBreaker.haltTrading("Halt from thread " + index);
                        halts.incrementAndGet();
                    } else {
                        boolean status = circuitBreaker.isHalted();
                        // status check is safe and non-blocking
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        assertTrue(halts.get() > 0);
        assertTrue(circuitBreaker.isHalted());
    }
}
