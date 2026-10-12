package com.tradecore.notification.event;

import java.math.BigDecimal;
import java.time.Instant;

/** Published inside the settlement transaction after ledger entries are written. */
public record TradeSettledEvent(
        String tradeId, String buyerId, String sellerId,
        BigDecimal price, long quantity, Instant settledAt) {
}
