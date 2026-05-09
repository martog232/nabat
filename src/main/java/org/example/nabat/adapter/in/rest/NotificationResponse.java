package org.example.nabat.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.Notification;
import org.example.nabat.domain.model.NotificationType;
import org.example.nabat.domain.model.UserId;

import java.time.Instant;
import java.util.UUID;

@Schema(description = "A user notification (vote event or milestone)")
public record NotificationResponse(
        @Schema(description = "Notification identifier") UUID id,
        @Schema(description = "ID of the user who receives this notification") UUID recipientId,
        @Schema(description = "What triggered the notification", example = "VOTE") NotificationType type,
        @Schema(description = "Short notification title", example = "New vote on your alert") String title,
        @Schema(description = "Notification body text") String message,
        @Schema(description = "Alert that triggered the notification; may be null") UUID relatedAlertId,
        @Schema(description = "User who caused the notification; may be null") UUID triggeredByUserId,
        @Schema(description = "Whether the notification has been read by the recipient") boolean read,
        @Schema(description = "Timestamp when the notification was created") Instant createdAt
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
