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
            String alertTitle
    ) {
        String title = switch (type) {
            case ALERT_UPVOTED -> "Your alert was upvoted";
            case ALERT_DOWNVOTED -> "Your alert was downvoted";
            case ALERT_CONFIRMED -> "Your alert was confirmed";
            default -> "New notification";
        };

        String message = String.format("Someone voted on '%s'", alertTitle);

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

    public static Notification createMileStoneNotification(
            UserId recipientId,
            AlertId alertId,
            String alertTitle,
            int confirmationCount
    ) {
        return new Notification(
                NotificationId.generate(),
                recipientId,
                NotificationType.ALERT_MILESTONE,
                "🎉 Milestone reached!",
                String.format("'%s' now has %d confirmations!", alertTitle, confirmationCount),
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
