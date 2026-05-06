package org.example.nabat.adapter.out.notification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.nabat.adapter.in.websocket.AlertWebSocketHandler;
import org.example.nabat.application.port.out.NotificationSender;
import org.example.nabat.domain.model.Notification;
import org.example.nabat.domain.model.UserId;
import org.springframework.stereotype.Component;

/**
 * Pushes notifications via WebSocket to online users.
 * Falls back to logging when the user is offline (notification is still persisted by the caller).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebSocketNotificationSender implements NotificationSender {

    private final AlertWebSocketHandler webSocketHandler;

    @Override
    public void sendToUser(UserId userId, Notification notification) {
        boolean delivered = webSocketHandler.sendNotificationToUser(userId.value(), notification);
        if (!delivered) {
            log.info("User {} offline; notification {} persisted but not pushed",
                    userId.value(), notification.id().value());
        }
    }

    @Override
    public boolean isUserOnline(UserId userId) {
        return webSocketHandler.isUserOnline(userId.value());
    }
}

