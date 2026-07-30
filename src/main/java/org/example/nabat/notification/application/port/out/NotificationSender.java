package org.example.nabat.notification.application.port.out;

import org.example.nabat.notification.domain.Notification;
import org.example.nabat.identity.domain.UserId;

public interface NotificationSender {

    /**
     * Delivers {@code notification} if the recipient is reachable, and does nothing
     * observable if they are not — the notification is persisted by the caller either way,
     * so an offline user finds it on their next read.
     */
    void sendToUser(UserId userId, Notification notification);
}
