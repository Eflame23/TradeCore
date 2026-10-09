package com.tradecore.ledger.model;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Shared Trade domain record representing an executed trade from the matching engine.
 *
 * @param tradeId     Unique identifier for the trade execution
 * @param buyOrderId  Order ID for the buying order
 * @param sellOrderId Order ID for the selling order
 * @param buyerId     User ID of the buyer
 * @param sellerId    User ID of the seller
 * @param price       Execution price (USD per BTC)
 * @param qty         Executed quantity of BTC (positive whole number)
 * @param ts          Execution timestamp
 */
public record Trade(
        String tradeId,
        String buyOrderId,
        String sellOrderId,
        String buyerId,
        String sellerId,
        BigDecimal price,
        long qty,
        Instant ts
) {
    public Trade {
        if (tradeId != null) {
            tradeId = tradeId.trim();
        }
        if (buyOrderId != null) {
            buyOrderId = buyOrderId.trim();
        }
        if (sellOrderId != null) {
            sellOrderId = sellOrderId.trim();
        }
        if (buyerId != null) {
            buyerId = buyerId.trim();
        }
        if (sellerId != null) {
            sellerId = sellerId.trim();
        }
    }
}
