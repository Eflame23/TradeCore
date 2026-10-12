package com.tradecore.risk.circuitbreaker;

import com.tradecore.risk.config.RiskProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe Circuit Breaker and Global Trading Halt coordinator.
 *
 * <p>Enforces emergency stops to prevent matching engine order processing during severe
 * exchange volatility, downstream infrastructure degradation, or elevated risk rejection rates.</p>
 *
 * <p><strong>Key Invariants:</strong></p>
 * <ul>
 *   <li>The halt state is managed via an {@link AtomicBoolean}. Reads and writes are completely thread-safe.</li>
 *   <li>An optional automatic tripping threshold tracks consecutive risk rejections. Once exceeded,
 *       trading automatically halts.</li>
 *   <li><strong>Safety Invariant:</strong> Once halted (either manually or automatically), trading NEVER resumes
 *       automatically. An explicit administrative action ({@link #resumeTrading()}) is required.</li>
 * </ul>
 */
@Component
public class TradingCircuitBreaker {

    private static final Logger log = LoggerFactory.getLogger(TradingCircuitBreaker.class);

    private final AtomicBoolean halted = new AtomicBoolean(false);
    private final AtomicInteger consecutiveRejections = new AtomicInteger(0);
    private final RiskProperties properties;

    public TradingCircuitBreaker(RiskProperties properties) {
        this.properties = properties;
    }

    /**
     * Checks if trading is currently halted globally.
     *
     * @return true if halted; false if active
     */
    public boolean isHalted() {
        return halted.get();
    }

    /**
     * Manually triggers a global trading halt.
     *
     * @param reason operational description of why trading was halted
     */
    public void haltTrading(String reason) {
        halted.set(true);
        log.warn("EMERGENCY: Global trading HALTED. Reason: {}", reason);
    }

    /**
     * Manually resumes global trading after administrative review.
     */
    public void resumeTrading() {
        consecutiveRejections.set(0);
        halted.set(false);
        log.info("OPERATIONAL: Global trading RESUMED by administrative action.");
    }

    /**
     * Records a successful order validation, resetting the consecutive rejections counter.
     */
    public void recordSuccess() {
        consecutiveRejections.set(0);
    }

    /**
     * Records a risk rejection. If auto-trip is enabled and the consecutive rejection threshold
     * is reached, trips the circuit breaker into halted mode.
     */
    public void recordRejection() {
        int currentCount = consecutiveRejections.incrementAndGet();
        RiskProperties.CircuitBreakerProperties cbProps = properties.getCircuitBreaker();
        if (cbProps.isAutoTripEnabled() && currentCount >= cbProps.getConsecutiveRejectionThreshold()) {
            if (halted.compareAndSet(false, true)) {
                log.error("CIRCUIT BREAKER TRIPPED AUTOMATICALLY: Exceeded {} consecutive risk rejections.",
                        currentCount);
            }
        }
    }

    public int getConsecutiveRejectionCount() {
        return consecutiveRejections.get();
    }
}
