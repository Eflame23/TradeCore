package com.tradecore.risk.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Shared risk order representation. */
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
        if (id != null) {
            id = id.trim();
        }
        if (userId != null) {
            userId = userId.trim();
        }
        if (symbol == null || symbol.isBlank()) {
            symbol = "BTC-USD";
        }
        if (orderType == null) {
            orderType = OrderType.LIMIT;
        }
    }

    public Order(String id, String userId, Side side, OrderType orderType, BigDecimal price, long qty, Instant ts) {
        this(id, "BTC-USD", userId, side, orderType, price, qty, ts);
    }

    public Order(String id, String userId, Side side, BigDecimal price, long qty, Instant ts) {
        this(id, "BTC-USD", userId, side, OrderType.LIMIT, price, qty, ts);
    }
}
