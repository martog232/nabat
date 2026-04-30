package org.example.nabat.adapter.in.rest;

import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.Notification;
import org.example.nabat.domain.model.NotificationType;
import org.example.nabat.domain.model.UserId;

import java.time.Instant;
import java.util.UUID;

public record NotificationResponse(
        UUID id,
        UUID recipientId,
        NotificationType type,
        String title,
        String message,
        UUID relatedAlertId,
        UUID triggeredByUserId,
        boolean read,
        Instant createdAt
) {
    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.id().value(),
                idOf(n.recipientId()),
                n.type(),
                n.title(),
                n.message(),
                n.relatedAlertId() != null ? alertIdOf(n.relatedAlertId()) : null,
                n.triggeredByUserId() != null ? idOf(n.triggeredByUserId()) : null,
                n.isRead(),
                n.createdAt()
        );
    }

    private static UUID idOf(UserId id) { return id.value(); }
    private static UUID alertIdOf(AlertId id) { return id.value(); }
}

