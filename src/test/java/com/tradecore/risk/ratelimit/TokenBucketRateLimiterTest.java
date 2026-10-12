package com.tradecore.risk.ratelimit;

import com.tradecore.risk.config.RiskProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("TokenBucketRateLimiter Tests")
class TokenBucketRateLimiterTest {

    private MutableClock testClock;
    private RiskProperties properties;
    private TokenBucketRateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        testClock = new MutableClock(Instant.ofEpochMilli(1_000_000L));
        properties = new RiskProperties();
        properties.setRateLimitCapacity(5); // 5 tokens
        properties.setRateLimitWindowSeconds(10L); // 10 seconds -> 0.5 tokens/sec
        rateLimiter = new TokenBucketRateLimiter(properties, testClock);
    }

    @Test
    @DisplayName("Should accept requests within rate limit capacity")
    void shouldAcceptRequestsWithinLimit() {
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.tryAcquire("user1"), "Request " + (i + 1) + " should succeed");
        }
    }

    @Test
    @DisplayName("Should reject requests exceeding capacity")
    void shouldRejectRequestsBeyondLimit() {
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.tryAcquire("user1"));
        }
        // 6th request must be rejected
        assertFalse(rateLimiter.tryAcquire("user1"), "Request beyond capacity should be rejected");
    }

    @Test
    @DisplayName("Different users should have independent rate limits")
    void shouldMaintainIndependentLimitsPerUser() {
        // Exhaust user1's tokens
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.tryAcquire("user1"));
        }
        assertFalse(rateLimiter.tryAcquire("user1"));

        // user2 must still have full 5 tokens
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.tryAcquire("user2"), "user2 request " + (i + 1) + " should succeed");
        }
        assertFalse(rateLimiter.tryAcquire("user2"));
    }

    @Test
    @DisplayName("Deterministic refill behavior using injectable test clock")
    void shouldRefillDeterministicallyOverTime() {
        // Consume all 5 tokens
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.tryAcquire("user1"));
        }
        assertFalse(rateLimiter.tryAcquire("user1"));

        // Advance clock by 2 seconds: refillRate is 5 / 10s = 0.5 tokens/sec -> 2 sec = 1 token
        testClock.advanceMillis(2000L);
        assertTrue(rateLimiter.tryAcquire("user1"), "After 2s, 1 token should be available");
        assertFalse(rateLimiter.tryAcquire("user1"), "Next immediate request should be rejected");

        // Advance clock by 10 seconds: bucket should fully refill to max capacity of 5
        testClock.advanceMillis(10_000L);
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.tryAcquire("user1"), "Request " + (i + 1) + " after full refill should succeed");
        }
        assertFalse(rateLimiter.tryAcquire("user1"));
    }

    @Test
    @DisplayName("Resetting user should restore full capacity")
    void shouldResetUserRateLimit() {
        for (int i = 0; i < 5; i++) {
            assertTrue(rateLimiter.tryAcquire("user1"));
        }
        assertFalse(rateLimiter.tryAcquire("user1"));

        rateLimiter.reset("user1");
        assertTrue(rateLimiter.tryAcquire("user1"), "After reset, request should succeed");
    }

    @Test
    @DisplayName("Concurrent requests cannot exceed the configured rate limit")
    void shouldHandleConcurrentRequestsThreadSafely() throws InterruptedException {
        int threads = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    if (rateLimiter.tryAcquire("concurrentUser")) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // Fire all threads simultaneously
        startLatch.countDown();
        doneLatch.await();
        executor.shutdown();

        assertEquals(5, successCount.get(), "Exactly 5 concurrent permits should be granted");
    }

    @Test
    @DisplayName("Eviction of inactive entries should bound memory growth")
    void shouldEvictInactiveEntries() {
        rateLimiter.tryAcquire("inactiveUser");
        assertEquals(1, rateLimiter.getTrackedKeyCount());

        // Advance clock past the 30-minute inactivity threshold
        testClock.advanceMillis(35 * 60_000L);
        rateLimiter.evictInactiveEntries();

        assertEquals(0, rateLimiter.getTrackedKeyCount(), "Stale user entry should be evicted");
    }

    /**
     * Controllable Clock for deterministic rate limiting testing.
     */
    static class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone = ZoneId.of("UTC");

        public MutableClock(Instant initialInstant) {
            this.instant = initialInstant;
        }

        public void advanceMillis(long millis) {
            this.instant = this.instant.plusMillis(millis);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
