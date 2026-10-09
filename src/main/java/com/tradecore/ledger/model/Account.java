package com.tradecore.ledger.model;

import java.math.BigDecimal;

/**
 * Account model representing the state of cash and asset balances.
 */
public record Account(
        String userId,
        BigDecimal cashBalance,
        BigDecimal assetBalance
) {}
