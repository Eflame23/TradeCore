package com.tradecore.risk.web.dto;

import com.tradecore.risk.model.Order;
import com.tradecore.risk.model.OrderType;
import com.tradecore.risk.model.Side;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * HTTP request body representing an incoming order submission.
 */
public record OrderRequest(
        String id,
        String symbol,
        String userId,
        String side,
        String orderType,
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

        OrderType resolvedType = OrderType.LIMIT;
        if (orderType != null && !orderType.isBlank()) {
            try {
                resolvedType = OrderType.valueOf(orderType.trim().toUpperCase());
            } catch (IllegalArgumentException ignored) {
                // Will default to LIMIT
            }
        }

        String resolvedSymbol = (symbol != null && !symbol.isBlank()) ? symbol.trim() : "BTC-USD";

        return new Order(
                id,
                resolvedSymbol,
                userId,
                resolvedSide,
                resolvedType,
                price,
                qty != null ? qty : 0L,
                Instant.now()
        );
    }
}
