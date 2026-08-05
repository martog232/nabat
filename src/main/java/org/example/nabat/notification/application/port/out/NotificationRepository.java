package org.example.nabat.notification.application.port.out;

import org.example.nabat.notification.domain.Notification;
import org.example.nabat.notification.domain.NotificationId;
import org.example.nabat.identity.domain.UserId;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository {

    Notification save(Notification notification);

    Optional<Notification> findById(NotificationId id);

    /** All notifications for a user, newest first. */
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(UserId recipientId);

    /** Unread notifications only, newest first. */
    List<Notification> findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(UserId recipientId);

    int countByRecipientIdAndIsReadFalse(UserId recipientId);

    void markAllAsReadByRecipientId(UserId recipientId);
}
