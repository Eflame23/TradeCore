package com.tradecore.risk.web.dto;

import com.tradecore.risk.model.Order;
import com.tradecore.risk.model.Side;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * HTTP request body representing an incoming limit order submission.
 */
public record OrderRequest(
        String id,
        String userId,
        String side,
        BigDecimal price,
        Long qty
) {
    public Order toDomain() {
        Side resolvedSide = null;
        if (side != null) {
            try {
                resolvedSide = Side.valueOf(side.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Will fail during risk validation
            }
        }

        return new Order(
                id,
                userId,
                resolvedSide,
                price,
                qty != null ? qty : 0L,
                Instant.now()
        );
    }
}
