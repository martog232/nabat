package org.example.nabat.adapter.in.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.example.nabat.domain.model.Alert;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.AlertSeverity;
import org.example.nabat.domain.model.AlertStatus;
import org.example.nabat.domain.model.AlertType;
import org.example.nabat.domain.model.Location;
import org.example.nabat.domain.model.Notification;
import org.example.nabat.domain.model.NotificationId;
import org.example.nabat.domain.model.NotificationType;
import org.example.nabat.domain.model.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertWebSocketHandlerTest {

    private ObjectMapper objectMapper;
    private AlertWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().findAndAddModules().build();
        handler = new AlertWebSocketHandler(objectMapper);
    }

    @Test
    void registersAuthenticatedSessionAndCleansUpOnClose() {
        UUID userId = UUID.randomUUID();
        WebSocketSession session = session(userId, true);

        handler.afterConnectionEstablished(session);

        assertTrue(handler.isUserOnline(userId));

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        assertFalse(handler.isUserOnline(userId));
    }

    @Test
    void registeredClosedSessionIsTreatedAsOffline() {
        UUID userId = UUID.randomUUID();
        WebSocketSession session = session(userId, false);

        handler.afterConnectionEstablished(session);

        assertFalse(handler.isUserOnline(userId));
        assertFalse(handler.sendNotificationToUser(userId, notification(UserId.of(userId))));
    }

    @Test
    void ignoresSessionWithoutAuthenticatedUserIdAttribute() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(new HashMap<>());
        when(session.getId()).thenReturn("session-without-user");

        handler.afterConnectionEstablished(session);
        handler.sendAlertToUser(UUID.randomUUID(), alert());

        verify(session, never()).sendMessage(any());
    }

    @Test
    void sendsAlertPayloadToOnlineUser() throws Exception {
        UUID userId = UUID.randomUUID();
        WebSocketSession session = session(userId, true);
        handler.afterConnectionEstablished(session);

        handler.sendAlertToUser(userId, alert());

        TextMessage message = capturedMessage(session);
        JsonNode json = objectMapper.readTree(message.getPayload());
        assertEquals("NEW_ALERT", json.get("type").asText());
        assertEquals("Road closure", json.get("alert").get("title").asText());
    }

    @Test
    void doesNotSendAlertToOfflineUser() throws Exception {
        UUID userId = UUID.randomUUID();

        handler.sendAlertToUser(userId, alert());

        WebSocketSession session = session(userId, true);
        verify(session, never()).sendMessage(any());
    }

    @Test
    void sendsNotificationPayloadAndReturnsTrueWhenDelivered() throws Exception {
        UUID userUuid = UUID.randomUUID();
        UserId userId = UserId.of(userUuid);
        WebSocketSession session = session(userUuid, true);
        handler.afterConnectionEstablished(session);

        boolean delivered = handler.sendNotificationToUser(userUuid, notification(userId));

        assertTrue(delivered);
        TextMessage message = capturedMessage(session);
        JsonNode json = objectMapper.readTree(message.getPayload());
        assertEquals("NOTIFICATION", json.get("type").asText());
        assertEquals("Notice", json.get("notification").get("title").asText());
    }

    @Test
    void returnsFalseForOfflineNotificationRecipient() {
        assertFalse(handler.sendNotificationToUser(UUID.randomUUID(), notification(UserId.generate())));
    }

    @Test
    void returnsFalseWhenNotificationSendFails() throws Exception {
        UUID userUuid = UUID.randomUUID();
        WebSocketSession session = session(userUuid, true);
        doThrow(new IOException("connection reset")).when(session).sendMessage(any(TextMessage.class));
        handler.afterConnectionEstablished(session);

        boolean delivered = handler.sendNotificationToUser(userUuid, notification(UserId.of(userUuid)));

        assertFalse(delivered);
    }

    @Test
    void alertSendFailureIsSwallowed() throws Exception {
        UUID userUuid = UUID.randomUUID();
        WebSocketSession session = session(userUuid, true);
        doThrow(new IOException("connection reset")).when(session).sendMessage(any(TextMessage.class));
        handler.afterConnectionEstablished(session);

        handler.sendAlertToUser(userUuid, alert());

        verify(session).sendMessage(any(TextMessage.class));
    }

    private static WebSocketSession session(UUID userId, boolean open) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(JwtHandshakeInterceptor.USER_ID_ATTR, userId);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.getId()).thenReturn("session-" + userId);
        when(session.isOpen()).thenReturn(open);
        return session;
    }

    private static TextMessage capturedMessage(WebSocketSession session) throws IOException {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        return captor.getValue();
    }

    private static Alert alert() {
        return new Alert(
            AlertId.generate(),
            "Road closure",
            "Main road is blocked",
            AlertType.OTHER,
            AlertSeverity.MEDIUM,
            Location.of(42.695, 23.329),
            Instant.now(),
            AlertStatus.ACTIVE,
            UUID.randomUUID(),
            0,
            0,
            0,
            null
        );
    }

    private static Notification notification(UserId recipientId) {
        return new Notification(
            NotificationId.generate(),
            recipientId,
            NotificationType.ALERT_UPVOTED,
            "Notice",
            "Someone voted",
            AlertId.generate(),
            UserId.generate(),
            false,
            Instant.now()
        );
    }
}


