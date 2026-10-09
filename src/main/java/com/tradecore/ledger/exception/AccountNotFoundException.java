package com.tradecore.ledger.exception;

/**
 * Thrown when an account involved in the trade does not exist in the database.
 */
public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String message) {
        super(message);
    }
}
