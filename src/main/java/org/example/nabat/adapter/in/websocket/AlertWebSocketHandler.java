package org.example.nabat.adapter.in.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.nabat.adapter.in.rest.AlertResponse;
import org.example.nabat.adapter.out.notification.RedisWsPublisher;
import org.example.nabat.domain.model.Alert;
import org.example.nabat.domain.model.Notification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AlertWebSocketHandler.class);

    private final Map<UUID, WebSocketSession> userSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final RedisWsPublisher redisWsPublisher;

    public AlertWebSocketHandler(ObjectMapper objectMapper, RedisWsPublisher redisWsPublisher) {
        this.objectMapper = objectMapper;
        this.redisWsPublisher = redisWsPublisher;
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
        AlertResponse alertResponse = AlertResponse.from(alert);
        if (!deliverLocally(userId, "NEW_ALERT", alertResponse)) {
            redisWsPublisher.publish(userId, "NEW_ALERT", alertResponse);
        }
    }

    /**
     * Delivers a message to the user's local WebSocket session.
     * Returns true if the user was connected to this instance and the message was sent.
     */
    public boolean deliverLocally(UUID userId, String type, Object payload) {
        WebSocketSession session = userSessions.get(userId);
        if (session == null || !session.isOpen()) {
            return false;
        }
        try {
            String json = objectMapper.writeValueAsString(
                new AlertResponseWrapper(type, payload)
            );
            session.sendMessage(new TextMessage(json));
            return true;
        } catch (IOException e) {
            log.warn("Failed to deliver {} to user {}: {}", type, userId, e.getMessage());
            return false;
        }
    }

    /** Pushes a notification to {@code userId}. Returns true if delivered, false if user offline. */
    public boolean sendNotificationToUser(UUID userId, Notification notification) {
        WebSocketSession session = userSessions.get(userId);
        if (session == null || !session.isOpen()) {
            return false;
        }
        try {
            String payload = objectMapper.writeValueAsString(
                Map.of("type", "NOTIFICATION", "notification", notification)
            );
            session.sendMessage(new TextMessage(payload));
            return true;
        } catch (IOException e) {
            log.warn("Failed to deliver notification to user {}: {}", userId, e.getMessage());
            return false;
        }
    }

    public boolean isUserOnline(UUID userId) {
        WebSocketSession session = userSessions.get(userId);
        return session != null && session.isOpen();
    }

    private UUID extractUserId(WebSocketSession session) {
        Object attr = session.getAttributes().get(JwtHandshakeInterceptor.USER_ID_ATTR);
        if (attr instanceof UUID uuid) {
            return uuid;
        }
        log.warn("WebSocket session {} has no authenticated userId attribute", session.getId());
        return null;
    }
}
