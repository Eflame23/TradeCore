package com.tradecore.notification.model;

import java.time.Instant;

/** Public API representation of a notification. */
public record NotificationResponse(
        long id, String tradeId, String userId, String type,
        String message, Instant createdAt, Instant readAt) {
}
