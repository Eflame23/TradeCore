-- TradeCore Part B: Ledger & Settlement Schema Migration

-- 1. accounts table
-- Holds cash (USD) and asset (BTC) balances for exchange users.
-- Constraints ensure balances cannot become negative.
CREATE TABLE IF NOT EXISTS accounts (
    user_id VARCHAR(64) PRIMARY KEY,
    cash_balance NUMERIC(20, 8) NOT NULL,
    asset_balance NUMERIC(20, 8) NOT NULL,
    CONSTRAINT chk_cash_balance_non_negative CHECK (cash_balance >= 0),
    CONSTRAINT chk_asset_balance_non_negative CHECK (asset_balance >= 0)
);

-- 2. settlements table
-- Tracks settled trades. The primary key on trade_id guarantees idempotency.
CREATE TABLE IF NOT EXISTS settlements (
    trade_id VARCHAR(64) PRIMARY KEY,
    settled_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 3. ledger_entries table
-- Double-entry audit ledger for financial traceability and zero-sum verification.
CREATE TABLE IF NOT EXISTS ledger_entries (
    id BIGSERIAL PRIMARY KEY,
    trade_id VARCHAR(64) NOT NULL REFERENCES settlements(trade_id) ON DELETE RESTRICT,
    user_id VARCHAR(64) NOT NULL REFERENCES accounts(user_id) ON DELETE RESTRICT,
    currency VARCHAR(10) NOT NULL CHECK (currency IN ('USD', 'BTC')),
    amount NUMERIC(20, 8) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Performance indices
CREATE INDEX IF NOT EXISTS idx_ledger_entries_trade_id ON ledger_entries(trade_id);
CREATE INDEX IF NOT EXISTS idx_ledger_entries_user_id ON ledger_entries(user_id);
