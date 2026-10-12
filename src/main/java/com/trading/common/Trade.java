package com.trading.common;

import java.math.BigDecimal;
import java.time.Instant;

/** Shared contract. Produced by the matching engine, consumed by Ledger and Events. */
public record Trade(String tradeId, String buyOrderId, String sellOrderId,
                    String buyerId, String sellerId,
                    BigDecimal price, long qty, Instant ts) {}
