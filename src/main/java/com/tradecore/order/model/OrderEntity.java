package com.tradecore.order.model;

import com.tradecore.risk.model.OrderType;
import com.tradecore.risk.model.Side;

import java.math.BigDecimal;
import java.time.Instant;

public record OrderEntity(
        String id,
        String symbol,
        String userId,
        Side side,
        OrderType orderType,
        BigDecimal price,
        long qty,
        long filledQty,
        String status, // OPEN, FILLED, PARTIALLY_FILLED, CANCELLED, REJECTED
        Instant createdAt,
        Instant updatedAt
) {}
