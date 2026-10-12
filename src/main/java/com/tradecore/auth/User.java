package com.tradecore.auth;

import java.time.Instant;

public record User(
        String id,
        String username,
        String passwordHash,
        String role, // USER, ADMIN
        Instant createdAt
) {}
