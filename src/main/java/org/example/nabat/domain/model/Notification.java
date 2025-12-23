package org.example.nabat.domain.model;

import java.time.Instant;

public record Notification(
    NotificationId id,
    UserId recipientId,       // Кой получава известието
    NotificationType type,    // Тип на известието
    String title,             // "Твоят alert беше потвърден"
    String message,           // "User123 потвърди 'Пожар на ул. Витоша'"
    AlertId relatedAlertId,   // Към кой alert е свързано (nullable)
    UserId triggeredByUserId, // Кой предизвика известието (nullable)
    boolean isRead,           // Прочетено ли е
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
            case ALERT_UPVOTED -> "Tвоят alert получи одобрение";
            case ALERT_DOWNVOTED -> "Tвоят alert получи неодобрение";
            case ALERT_CONFIRMED -> "Tвоят alert беше потвърден";
            default -> "Ново известие";
        };

        String message = String.format("Някой гласува за '%s'", alertTitle);

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
                "🎉 Milestone достигнат!",
                String.format("'%s' има вече %d потвърждения!", alertTitle, confirmationCount),
                alertId,
                null,  // Няма конкретен voter
                false,
                Instant.now()
        );
    }

    public Notification markAsRead() {
        return new Notification(
                this.id,
                this.recipientId,
                this.type,
                this.title,
                this.message,
                this.relatedAlertId,
                this.triggeredByUserId,
                true,  // Маркирано като прочетено
                this.createdAt
        );
    }
}
