package org.example.nabat.application.port.out;

import org.example.nabat.domain.model.Notification;
import org.example.nabat.domain.model.UserId;

public interface NotificationSender {
    void sendToUser(UserId userId, Notification notification);
    boolean isUserOnline(UserId userId);
}
