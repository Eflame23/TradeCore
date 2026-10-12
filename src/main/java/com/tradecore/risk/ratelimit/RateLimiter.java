package com.tradecore.risk.ratelimit;

/**
 * Contract for enforcing per-user rate limits.
 */
public interface RateLimiter {

    /**
     * Attempts to acquire an execution permit for the specified key (typically userId).
     *
     * @param key the identifier representing the entity being rate-limited (e.g. userId)
     * @return true if permitted, false if rate limit exceeded
     */
    boolean tryAcquire(String key);

    /**
     * Resets rate limit statistics for a given user.
     *
     * @param key the identifier to reset
     */
    void reset(String key);

    /**
     * Evicts stale or inactive rate limiter records to prevent unbounded memory growth.
     */
    void evictInactiveEntries();

    /**
     * Returns the current number of tracked active keys in the rate limiter cache.
     */
    int getTrackedKeyCount();
}
