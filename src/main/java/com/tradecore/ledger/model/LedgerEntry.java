package com.tradecore.ledger.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Representation of an individual audit entry in the ledger.
 */
public record LedgerEntry(
        Long id,
        String tradeId,
        String userId,
        String currency,
        BigDecimal amount,
        Instant createdAt
) {}
