package com.trading.common;

import java.math.BigDecimal;
import java.time.Instant;

/** Shared contract (agreed with the whole team). Immutable. */
public record Order(String id, String userId, Side side, BigDecimal price, long qty, Instant ts) {}
