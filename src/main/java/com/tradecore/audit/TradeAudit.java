package com.tradecore.audit;

import java.math.BigDecimal;
import java.time.Instant;

public record TradeAudit(
        Long id,
        String tradeId,
        String buyOrderId,
        String sellOrderId,
        String buyerId,
        String sellerId,
        BigDecimal price,
        long qty,
        String matchReason,
        Instant createdAt
) {}
