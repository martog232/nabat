package org.example.nabat.domain.model;

import java.time.Instant;

public record Notification(
    NotificationId id,
    UserId recipientId,       // recipient user
    NotificationType type,
    String title,
    String message,
    AlertId relatedAlertId,   // related alert (nullable)
    UserId triggeredByUserId, // user that triggered the notification (nullable)
    boolean isRead,
    Instant createdAt
){

    public static Notification createVoteNotification(
            UserId recipientId,
            NotificationType type,
            AlertId alertId,
            UserId voterId,
            String title,
            String message
    ) {
        return new Notification(
                NotificationId.generate(),
                recipientId,
                type,
                title,
                message,
                alertId,
                voterId,
                false,
                Instant.now()
        );
    }

    public static Notification createMilestoneNotification(
            UserId recipientId,
            AlertId alertId,
            String title,
            String message
    ) {
        return new Notification(
                NotificationId.generate(),
                recipientId,
                NotificationType.ALERT_MILESTONE,
                title,
                message,
                alertId,
                null,
                false,
                Instant.now()
        );
    }

    /** Returns a copy of this notification with isRead = true. Notification is immutable. */
    public Notification markAsRead() {
        return new Notification(
                this.id,
                this.recipientId,
                this.type,
                this.title,
                this.message,
                this.relatedAlertId,
                this.triggeredByUserId,
                true,
                this.createdAt
        );
    }
}
