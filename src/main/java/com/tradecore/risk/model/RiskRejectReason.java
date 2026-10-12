package com.tradecore.risk.model;

/**
 * Standardized categorization for risk check rejections.
 */
public enum RiskRejectReason {
    TRADING_HALTED,
    RATE_LIMIT_EXCEEDED,
    ORDER_NULL,
    ORDER_ID_INVALID,
    USER_ID_INVALID,
    SIDE_INVALID,
    INVALID_QUANTITY,
    INVALID_PRICE,
    MAX_QUANTITY_EXCEEDED,
    POSITION_LIMIT_EXCEEDED,
    POSITION_DATA_UNAVAILABLE
}
