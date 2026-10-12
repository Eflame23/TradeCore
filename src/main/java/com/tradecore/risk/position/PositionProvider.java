package com.tradecore.risk.position;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Strategy interface providing current asset holdings for a given user.
 */
public interface PositionProvider {

    /**
     * Retrieves the user's current net asset position (e.g. BTC balance).
     *
     * @param userId user identifier
     * @return Optional containing current position if available, or empty if user account does not exist
     *         or position data cannot be determined reliably
     */
    Optional<BigDecimal> getPosition(String userId);

    /**
     * Checks if this provider is currently connected and healthy.
     *
     * @return true if position lookups can be reliably fulfilled, false otherwise
     */
    boolean isAvailable();
}
