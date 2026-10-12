package com.tradecore.risk.ratelimit;

import com.tradecore.risk.config.RiskProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Thread-safe, in-memory Token Bucket rate limiter.
 *
 * <p>Each unique key (userId) maintains an independent token bucket. Tokens replenish
 * smoothly over time based on the configured rate limit window and capacity.</p>
 *
 * <p><strong>Key Concurrency & Memory Design:</strong></p>
 * <ul>
 *   <li>Buckets are mapped via {@link ConcurrentHashMap}.</li>
 *   <li>Each individual {@link Bucket} uses an isolated {@link ReentrantLock} to serialize token
 *       consumption and replenishment per user, ensuring thread safety without global lock contention.</li>
 *   <li>To avoid unbounded heap growth from transient or inactive users, {@link #evictInactiveEntries()}
 *       periodically removes buckets that have been inactive past the configured inactivity threshold.</li>
 *   <li>Accepts an injectable {@link Clock} for fully deterministic unit and integration testing.</li>
 * </ul>
 *
 * <p><strong>Prototype Limitation:</strong> This rate limiter is strictly in-memory. Counters
 * reset upon application restart and are not shared across clustered application instances. For a
 * distributed deployment, a Redis-backed token bucket (e.g., via Redisson or Lua scripts) is required.</p>
 */
@Component
public class TokenBucketRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(TokenBucketRateLimiter.class);

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final RiskProperties properties;
    private final Clock clock;

    public TokenBucketRateLimiter(RiskProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public boolean tryAcquire(String key) {
        if (key == null || key.isBlank()) {
            return false;
        }

        // Bounded capacity guard: if size exceeds maximum configured entries, proactively evict
        if (buckets.size() > properties.getRateLimiterMaxEntries()) {
            evictInactiveEntries();
        }

        Bucket bucket = buckets.computeIfAbsent(key, k -> new Bucket(
                properties.getRateLimitCapacity(),
                properties.getRateLimitWindowSeconds(),
                clock.millis()
        ));

        return bucket.tryConsume(clock.millis());
    }

    @Override
    public void reset(String key) {
        if (key != null) {
            buckets.remove(key);
        }
    }

    @Override
    public void evictInactiveEntries() {
        long now = clock.millis();
        long inactivityThresholdMillis = properties.getRateLimiterInactivityMinutes() * 60_000L;

        buckets.entrySet().removeIf(entry -> {
            Bucket bucket = entry.getValue();
            return (now - bucket.getLastAccessMillis()) > inactivityThresholdMillis;
        });
        log.debug("Evicted inactive rate limiter buckets. Active tracked keys: {}", buckets.size());
    }

    @Override
    public int getTrackedKeyCount() {
        return buckets.size();
    }

    /**
     * Isolated token bucket state for a single user.
     */
    static class Bucket {
        private final double capacity;
        private final double refillRatePerMillis; // tokens per millisecond
        private final ReentrantLock lock = new ReentrantLock();

        private double availableTokens;
        private long lastRefillMillis;
        private volatile long lastAccessMillis;

        public Bucket(int capacity, long windowSeconds, long nowMillis) {
            this.capacity = capacity;
            this.availableTokens = capacity;
            this.lastRefillMillis = nowMillis;
            this.lastAccessMillis = nowMillis;
            // Refill rate: capacity tokens / (windowSeconds * 1000) ms
            this.refillRatePerMillis = (double) capacity / (windowSeconds * 1000.0);
        }

        public boolean tryConsume(long nowMillis) {
            lock.lock();
            try {
                this.lastAccessMillis = nowMillis;

                // Calculate replenished tokens since last refill
                long elapsedMillis = Math.max(0, nowMillis - lastRefillMillis);
                if (elapsedMillis > 0) {
                    double tokensToAdd = elapsedMillis * refillRatePerMillis;
                    availableTokens = Math.min(capacity, availableTokens + tokensToAdd);
                    lastRefillMillis = nowMillis;
                }

                if (availableTokens >= 1.0) {
                    availableTokens -= 1.0;
                    return true;
                }
                return false;
            } finally {
                lock.unlock();
            }
        }

        public long getLastAccessMillis() {
            return lastAccessMillis;
        }

        public double getAvailableTokens() {
            lock.lock();
            try {
                return availableTokens;
            } finally {
                lock.unlock();
            }
        }
    }
}
