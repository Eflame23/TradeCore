package com.tradecore.risk.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;

/**
 * Externalized configuration properties for TradeCore Part D Risk & Rate Limiting.
 * Configured under prefix {@code tradecore.risk}.
 */
@ConfigurationProperties(prefix = "tradecore.risk")
public class RiskProperties {

    /**
     * Maximum quantity allowed for a single order. Defaults to 10 (e.g. 10 BTC).
     */
    private long maxOrderQuantity = 10L;

    /**
     * Maximum absolute net position allowed for any single user. Defaults to 100 (e.g. 100 BTC).
     */
    private BigDecimal maxAbsolutePosition = new BigDecimal("100");

    /**
     * Maximum number of order submissions allowed per user within the rate limit window.
     * Defaults to 20.
     */
    private int rateLimitCapacity = 20;

    /**
     * Window duration in seconds for the rate limiter. Defaults to 60 seconds (1 minute).
     */
    private long rateLimitWindowSeconds = 60L;

    /**
     * Maximum number of entries kept in the rate limiter cache before cleaning up inactive entries.
     */
    private int rateLimiterMaxEntries = 10_000;

    /**
     * Inactivity threshold in minutes after which a rate limiter bucket can be evicted.
     */
    private long rateLimiterInactivityMinutes = 30L;

    /**
     * Circuit breaker configuration.
     */
    private CircuitBreakerProperties circuitBreaker = new CircuitBreakerProperties();

    public static class CircuitBreakerProperties {
        /**
         * Whether the automatic circuit breaker trip on consecutive rejections is enabled.
         * Disabled by default for prototype flexibility.
         */
        private boolean autoTripEnabled = false;

        /**
         * Threshold of consecutive risk rejections required to trigger an automatic trading halt.
         */
        private int consecutiveRejectionThreshold = 50;

        public boolean isAutoTripEnabled() {
            return autoTripEnabled;
        }

        public void setAutoTripEnabled(boolean autoTripEnabled) {
            this.autoTripEnabled = autoTripEnabled;
        }

        public int getConsecutiveRejectionThreshold() {
            return consecutiveRejectionThreshold;
        }

        public void setConsecutiveRejectionThreshold(int consecutiveRejectionThreshold) {
            this.consecutiveRejectionThreshold = consecutiveRejectionThreshold;
        }
    }

    public long getMaxOrderQuantity() {
        return maxOrderQuantity;
    }

    public void setMaxOrderQuantity(long maxOrderQuantity) {
        this.maxOrderQuantity = maxOrderQuantity;
    }

    public BigDecimal getMaxAbsolutePosition() {
        return maxAbsolutePosition;
    }

    public void setMaxAbsolutePosition(BigDecimal maxAbsolutePosition) {
        this.maxAbsolutePosition = maxAbsolutePosition;
    }

    public int getRateLimitCapacity() {
        return rateLimitCapacity;
    }

    public void setRateLimitCapacity(int rateLimitCapacity) {
        this.rateLimitCapacity = rateLimitCapacity;
    }

    public long getRateLimitWindowSeconds() {
        return rateLimitWindowSeconds;
    }

    public void setRateLimitWindowSeconds(long rateLimitWindowSeconds) {
        this.rateLimitWindowSeconds = rateLimitWindowSeconds;
    }

    public int getRateLimiterMaxEntries() {
        return rateLimiterMaxEntries;
    }

    public void setRateLimiterMaxEntries(int rateLimiterMaxEntries) {
        this.rateLimiterMaxEntries = rateLimiterMaxEntries;
    }

    public long getRateLimiterInactivityMinutes() {
        return rateLimiterInactivityMinutes;
    }

    public void setRateLimiterInactivityMinutes(long rateLimiterInactivityMinutes) {
        this.rateLimiterInactivityMinutes = rateLimiterInactivityMinutes;
    }

    public CircuitBreakerProperties getCircuitBreaker() {
        return circuitBreaker;
    }

    public void setCircuitBreaker(CircuitBreakerProperties circuitBreaker) {
        this.circuitBreaker = circuitBreaker;
    }
}
