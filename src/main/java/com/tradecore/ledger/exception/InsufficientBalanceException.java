package com.tradecore.ledger.exception;

/**
 * Thrown when an account does not have sufficient cash (USD) or asset (BTC) to settle the trade.
 */
public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(String message) {
        super(message);
    }
}
