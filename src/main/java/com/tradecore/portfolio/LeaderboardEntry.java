package com.tradecore.portfolio;

import java.math.BigDecimal;

public record LeaderboardEntry(
        int rank,
        String userId,
        BigDecimal totalEquity,
        BigDecimal cashBalance,
        BigDecimal assetBalance,
        BigDecimal pnlAmount
) {}
