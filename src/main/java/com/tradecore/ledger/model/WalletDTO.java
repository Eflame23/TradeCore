package com.tradecore.ledger.model;

import java.math.BigDecimal;

public record WalletDTO(
        String userId,
        BigDecimal cashBalance,
        BigDecimal assetBalance,
        BigDecimal reservedCash,
        BigDecimal reservedAsset,
        BigDecimal availableCash,
        BigDecimal availableAsset
) {}
