package com.tradecore.ledger.exception;

/**
 * Thrown when trade data fails validation (null/blank fields, self-trade, negative price/qty).
 */
public class TradeValidationException extends RuntimeException {
    public TradeValidationException(String message) {
        super(message);
    }
}
