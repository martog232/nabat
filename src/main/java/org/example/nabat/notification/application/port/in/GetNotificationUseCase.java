package org.example.nabat.notification.application.port.in;

import org.example.nabat.notification.domain.Notification;
import org.example.nabat.notification.domain.NotificationId;
import org.example.nabat.identity.domain.UserId;

import java.util.List;

public interface GetNotificationUseCase {

    List<Notification> getNotifications(UserId userId);

    List<Notification> getUnreadNotifications(UserId userId);

    int countUnreadNotifications(UserId userId);

    Notification markAsRead(NotificationId notificationId, UserId userId);

    void markAllAsRead(UserId userId);
}
