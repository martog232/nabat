package org.example.nabat.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public record WebSocketTicket(
    String value,
    UserId userId,
    Instant expiresAt
) {
    public WebSocketTicket {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("WebSocket ticket value is required");
        }
        if (userId == null) {
            throw new IllegalArgumentException("WebSocket ticket userId is required");
        }
        if (expiresAt == null) {
            throw new IllegalArgumentException("WebSocket ticket expiry is required");
        }
    }

    public static WebSocketTicket issueFor(UserId userId, Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("WebSocket ticket TTL must be positive");
        }
        return new WebSocketTicket(
            UUID.randomUUID().toString(),
            userId,
            Instant.now().plus(ttl)
        );
    }

    public boolean isExpiredAt(Instant instant) {
        return !expiresAt.isAfter(instant);
    }
}

