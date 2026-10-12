package com.tradecore.news;

import java.time.Instant;

public record MarketNews(
        String id,
        String headline,
        String category, // CRYPTO, EQUITY, COMMODITY, MACRO
        String source,
        String sentiment, // BULLISH, BEARISH, NEUTRAL
        Instant timestamp
) {}
