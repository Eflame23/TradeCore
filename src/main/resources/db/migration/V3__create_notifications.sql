-- Member 4: Notification/Event Layer
-- A notification is unique per trade, user and event type, preventing duplicate confirmations.
CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    trade_id VARCHAR(64) NOT NULL REFERENCES settlements(trade_id) ON DELETE RESTRICT,
    user_id VARCHAR(64) NOT NULL REFERENCES accounts(user_id) ON DELETE RESTRICT,
    type VARCHAR(40) NOT NULL,
    message VARCHAR(500) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMP WITH TIME ZONE NULL,
    CONSTRAINT uq_notification_trade_user_type UNIQUE (trade_id, user_id, type)
);
CREATE INDEX IF NOT EXISTS idx_notifications_user_created ON notifications(user_id, created_at DESC);
