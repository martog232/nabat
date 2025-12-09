package org.example.nabat.adapter.in.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.nabat.domain.model.Alert;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AlertWebSocketHandler extends TextWebSocketHandler {

    private final Map<UUID, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;

    public AlertWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID userId = extractUserId(session);
        if (userId != null) {
            userSessions.put(userId, session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID userId = extractUserId(session);
        if (userId != null) {
            userSessions.remove(userId);
        }
    }

    public void sendAlertToUser(UUID userId, Alert alert) {
        WebSocketSession session = userSessions. get(userId);
        if (session != null && session.isOpen()) {
            try {
                String payload = objectMapper.writeValueAsString(
                    new AlertNotification("NEW_ALERT", alert)
                );
                session.sendMessage(new TextMessage(payload));
            } catch (IOException e) {
                // Log error
            }
        }
    }

    private UUID extractUserId(WebSocketSession session) {
        String userIdParam = session.getUri().getQuery();
        // Parse userId from query params
        if (userIdParam != null && userIdParam.startsWith("userId=")) {
            return UUID. fromString(userIdParam.substring(7));
        }
        return null;
    }

    private record AlertNotification(String type, Alert alert) {}
}
