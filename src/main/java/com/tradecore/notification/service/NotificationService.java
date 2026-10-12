package com.tradecore.notification.service;

import com.tradecore.notification.event.TradeSettledEvent;
import com.tradecore.notification.model.NotificationResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

/** Persists and retrieves notifications for the prototype REST-polling workflow. */
@Service
public class NotificationService {
    private final JdbcTemplate jdbcTemplate;

    public NotificationService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Idempotently creates one trade confirmation for each participant in a new transaction. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void createTradeSettledNotifications(TradeSettledEvent event) {
        String message = String.format(
                "Trade %s settled successfully: %s BTC at $%s per BTC.",
                event.tradeId(), event.quantity(), event.price().stripTrailingZeros().toPlainString());
        insertIfMissing(event.tradeId(), event.buyerId(), "TRADE_SETTLED", message, event.settledAt());
        insertIfMissing(event.tradeId(), event.sellerId(), "TRADE_SETTLED", message, event.settledAt());
    }

    private void insertIfMissing(String tradeId, String userId, String type, String message, Instant createdAt) {
        jdbcTemplate.update(
                "INSERT INTO notifications (trade_id, user_id, type, message, created_at) " +
                "VALUES (?, ?, ?, ?, ?) ON CONFLICT (trade_id, user_id, type) DO NOTHING",
                tradeId, userId, type, message, Timestamp.from(createdAt));
    }

    public List<NotificationResponse> getNotificationsForUser(String userId) {
        return jdbcTemplate.query(
                "SELECT id, trade_id, user_id, type, message, created_at, read_at " +
                "FROM notifications WHERE user_id = ? ORDER BY created_at DESC, id DESC",
                (rs, rowNum) -> new NotificationResponse(
                        rs.getLong("id"), rs.getString("trade_id"), rs.getString("user_id"),
                        rs.getString("type"), rs.getString("message"),
                        rs.getTimestamp("created_at").toInstant(),
                        rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant()),
                userId);
    }
}
