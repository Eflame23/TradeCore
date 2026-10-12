package com.tradecore.portfolio;

import java.math.BigDecimal;

public record Candle(
        long time, // Epoch timestamp in seconds
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        long volume
) {}
