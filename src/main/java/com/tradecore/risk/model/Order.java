package com.tradecore.risk.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Shared or risk-tier Order representation.
 * Immutable record representing an incoming limit order.
 *
 * @param id      Unique identifier for the order
 * @param userId  User submitting the order
 * @param side    Side of the order (BUY or SELL)
 * @param price   Limit price (positive BigDecimal)
 * @param qty     Order quantity (positive integer/long)
 * @param ts      Order creation/submission timestamp
 */
public record Order(
        String id,
        String userId,
        Side side,
        BigDecimal price,
        long qty,
        Instant ts
) {
    public Order {
        if (id != null) {
            id = id.trim();
        }
        if (userId != null) {
            userId = userId.trim();
        }
    }
}
