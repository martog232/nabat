package org.example.nabat.application.port.out;

import org.example.nabat.domain.model.Notification;
import org.example.nabat.domain.model.NotificationId;
import org.example.nabat.domain.model.UserId;

import java.util.List;
import java.util.Optional;

public interface NotificationRepository {

    Notification save(Notification notification);

    // Търсене по ID
    Optional<Notification> findById(NotificationId id);

    // Всички известия за потребител (сортирани по дата, най-новите първи)
    List<Notification> findByRecipientIdOrderByCreatedAtDesc(UserId recipientId);

    // Само непрочетени
    List<Notification> findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(UserId recipientId);

    // Броене на непрочетени
    int countByRecipientIdAndIsReadFalse(UserId recipientId);

    // Маркиране на всички като прочетени
    void markAllAsReadByRecipientId(UserId recipientId);
}
