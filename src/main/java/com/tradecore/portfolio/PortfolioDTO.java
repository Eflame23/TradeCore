package com.tradecore.portfolio;

import java.math.BigDecimal;

public record PortfolioDTO(
        String userId,
        BigDecimal cashBalance,
        BigDecimal assetBalance,
        BigDecimal marketPrice,
        BigDecimal assetValueUsd,
        BigDecimal totalEquity,
        BigDecimal pnlAmount,
        BigDecimal pnlPercentage
) {}
