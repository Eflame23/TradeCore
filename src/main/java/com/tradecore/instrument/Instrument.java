package com.tradecore.instrument;

import java.math.BigDecimal;

public record Instrument(
        String symbol,
        String tvSymbol, // TradingView Symbol (e.g., BINANCE:BTCUSDT, NASDAQ:AAPL, TVC:GOLD)
        String name,
        String category, // CRYPTO, EQUITY, COMMODITY
        BigDecimal price,
        BigDecimal changePercent,
        BigDecimal volume24h,
        BigDecimal high24h,
        BigDecimal low24h
) {}
