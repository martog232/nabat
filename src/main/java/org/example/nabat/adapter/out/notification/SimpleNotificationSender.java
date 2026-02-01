package org.example.nabat.adapter.out.notification;

import org.example.nabat.application.port.out.NotificationSender;
import org.example.nabat.domain.model.Notification;
import org.example.nabat.domain.model.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Simple notification sender implementation.
 * In a production system, this would integrate with WebSocket, push notifications, etc.
 */
@Component
public class SimpleNotificationSender implements NotificationSender {

    private static final Logger log = LoggerFactory.getLogger(SimpleNotificationSender.class);

    @Override
    public void sendToUser(UserId userId, Notification notification) {
        // For now, just log the notification
        // In production, this would send via WebSocket, push notification, etc.
        log.info("Sending notification to user {}: {} - {}",
                userId.value(),
                notification.title(),
                notification.message());
    }

    @Override
    public boolean isUserOnline(UserId userId) {
        // For now, always return false
        // In production, this would check WebSocket connection status
        return false;
    }
}

