package org.example.nabat.application.port.in;

import org.example.nabat.domain.model.Notification;
import org.example.nabat.domain.model.NotificationId;
import org.example.nabat.domain.model.UserId;

import java.util.List;

public interface GetNotificationUseCase {

    List<Notification> getNotifications(UserId userId);

    List<Notification> getUnreadNotifications(UserId userId);

    int countUnreadNotifications(UserId userId);

    Notification markAsRead(NotificationId notificationId, UserId userId);

    void markAllAsRead(UserId userId);
}
