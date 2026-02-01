package org.example.nabat.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.example.nabat.domain.model.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "notifications")
public class NotificationJpaEntity {

    @Id
    private UUID id;

    @Column(name = "recipient_id", nullable = false)
    private UUID recipientId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String message;

    @Column(name = "related_alert_id")
    private UUID relatedAlertId;

    @Column(name = "triggered_by_user_id")
    private UUID triggeredByUserId;

    @Column(name = "is_read", nullable = false)
    private boolean isRead = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static NotificationJpaEntity from(Notification notification) {
        NotificationJpaEntity entity = new NotificationJpaEntity();
        entity.id = notification.id().value();
        entity.recipientId = notification.recipientId().value();
        entity.type = notification.type();
        entity.title = notification.title();
        entity.message = notification.message();
        entity.relatedAlertId = notification.relatedAlertId() != null ? notification.relatedAlertId().value() : null;
        entity.triggeredByUserId = notification.triggeredByUserId() != null ? notification.triggeredByUserId().value() : null;
        entity.isRead = notification.isRead();
        entity.createdAt = notification.createdAt();
        return entity;
    }

    public Notification toDomain() {
        return new Notification(
                NotificationId.of(id),
                UserId.of(recipientId),
                type,
                title,
                message,
                relatedAlertId != null ? AlertId.of(relatedAlertId) : null,
                triggeredByUserId != null ? UserId.of(triggeredByUserId) : null,
                isRead,
                createdAt
        );
    }
}

