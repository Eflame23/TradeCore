package com.trading.common;

import java.math.BigDecimal;
import java.time.Instant;

/** Shared contract. Immutable. */
public record Order(
        String id,
        String symbol,
        String userId,
        Side side,
        OrderType orderType,
        BigDecimal price,
        long qty,
        Instant ts
) {
    public Order {
        if (symbol == null || symbol.isBlank()) {
            symbol = "BTC-USD";
        }
    }

    public Order(String id, String userId, Side side, OrderType orderType, BigDecimal price, long qty, Instant ts) {
        this(id, "BTC-USD", userId, side, orderType, price, qty, ts);
    }

    public Order(String id, String userId, Side side, BigDecimal price, long qty, Instant ts) {
        this(id, "BTC-USD", userId, side, OrderType.LIMIT, price, qty, ts);
    }
}
