package org.example.nabat.adapter.out.notification;

import org.example.nabat.adapter.in.websocket.AlertWebSocketHandler;
import org.example.nabat.application.port.out.AlertNotificationPort;
import org.example.nabat.domain.model.Alert;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
public class WebSocketAlertNotificationAdapter implements AlertNotificationPort {

    private final AlertWebSocketHandler webSocketHandler;

    public WebSocketAlertNotificationAdapter(AlertWebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    @Override
    public void broadcastAlert(Alert alert, List<UUID> userIds) {
        userIds.forEach(userId -> webSocketHandler.sendAlertToUser(userId, alert));
    }

    @Override
    public void notifyUser(UUID userId, Alert alert) {
        webSocketHandler.sendAlertToUser(userId, alert);
    }
}

