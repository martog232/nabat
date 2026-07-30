package org.example.nabat.realtime.adapter.in.websocket;

import org.example.nabat.realtime.spi.WsFrame;
import org.example.nabat.realtime.spi.WsClusterRelay;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
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

/**
 * The handler routes and serialises frames; it does not know what they carry. These
 * tests therefore use a stand-in payload rather than the incident and notification
 * DTOs — building those here would recreate the very dependency on other modules that
 * moving frame construction out of the handler removed.
 */
class AlertWebSocketHandlerTest {

    /** Stand-in for whatever a producing module puts in a frame. */
    private record Payload(String title) {}

    private ObjectMapper objectMapper;
    private WsClusterRelay clusterRelay;
    private AlertWebSocketHandler handler;

    @BeforeEach
    void setUp() {
        objectMapper = JsonMapper.builder().findAndAddModules().build();
        // The handler depends on the relay interface rather than on the Redis
        // publisher class directly.
        clusterRelay = mock(WsClusterRelay.class);
        handler = new AlertWebSocketHandler(objectMapper, clusterRelay, 5000);
    }

    @Test
    void keepsBothSessionsWhenAUserOpensTwoTabs() throws Exception {
        UUID userId = UUID.randomUUID();
        WebSocketSession firstTab = session(userId, true, "tab-1");
        WebSocketSession secondTab = session(userId, true, "tab-2");

        handler.afterConnectionEstablished(firstTab);
        handler.afterConnectionEstablished(secondTab);

        handler.sendToUser(userId, newAlertFrame());

        // Both tabs receive the frame. Sessions used to be keyed by user alone, so the
        // second connection evicted the first.
        verify(firstTab).sendMessage(any(TextMessage.class));
        verify(secondTab).sendMessage(any(TextMessage.class));
    }

    @Test
    void closingOneTabDoesNotDisconnectTheOther() {
        UUID userId = UUID.randomUUID();
        WebSocketSession firstTab = session(userId, true, "tab-1");
        WebSocketSession secondTab = session(userId, true, "tab-2");

        handler.afterConnectionEstablished(firstTab);
        handler.afterConnectionEstablished(secondTab);

        handler.afterConnectionClosed(firstTab, CloseStatus.NORMAL);

        // Previously removal was by user id, so closing the first tab deleted the entry
        // belonging to the second and silently cut off a live client.
        assertTrue(handler.isUserOnline(userId));
    }

    @Test
    void broadcastDeliversLocallyAndRelaysOnce() {
        UUID userId = UUID.randomUUID();
        handler.afterConnectionEstablished(session(userId, true, "tab-1"));

        handler.broadcast(WsFrame.alert(WsFrame.ALERT_UPDATED, new Payload("Road closure")));

        verify(clusterRelay).relayBroadcast(any(WsFrame.class));
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
        assertFalse(handler.sendToUser(userId, notificationFrame()));
    }

    @Test
    void ignoresSessionWithoutAuthenticatedUserIdAttribute() throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getAttributes()).thenReturn(new HashMap<>());
        when(session.getId()).thenReturn("session-without-user");

        handler.afterConnectionEstablished(session);
        handler.sendToUser(UUID.randomUUID(), newAlertFrame());

        verify(session, never()).sendMessage(any());
    }

    @Test
    void sendsAlertPayloadToOnlineUser() throws Exception {
        UUID userId = UUID.randomUUID();
        WebSocketSession session = session(userId, true);
        handler.afterConnectionEstablished(session);

        handler.sendToUser(userId, newAlertFrame());

        TextMessage message = capturedMessage(session);
        JsonNode json = objectMapper.readTree(message.getPayload());
        assertEquals("NEW_ALERT", json.get("type").asText());
        assertEquals("Road closure", json.get("alert").get("title").asText());
    }

    @Test
    void doesNotSendAlertToOfflineUser() throws Exception {
        UUID userId = UUID.randomUUID();

        handler.sendToUser(userId, newAlertFrame());

        WebSocketSession session = session(userId, true);
        verify(session, never()).sendMessage(any());
    }

    @Test
    void relaysToPeersWhenRecipientHasNoLocalSession() {
        UUID userId = UUID.randomUUID();

        assertFalse(handler.sendToUser(userId, newAlertFrame()));

        // Without the relay, frames for users connected to another replica were dropped
        // and only surfaced on the next REST poll.
        verify(clusterRelay).relayToUser(any(UUID.class), any(WsFrame.class));
    }

    @Test
    void sendsNotificationPayloadAndReturnsTrueWhenDelivered() throws Exception {
        UUID userId = UUID.randomUUID();
        WebSocketSession session = session(userId, true);
        handler.afterConnectionEstablished(session);

        boolean delivered = handler.sendToUser(userId, notificationFrame());

        assertTrue(delivered);
        TextMessage message = capturedMessage(session);
        JsonNode json = objectMapper.readTree(message.getPayload());
        assertEquals("NOTIFICATION", json.get("type").asText());
        assertEquals("Notice", json.get("notification").get("title").asText());
    }

    @Test
    void returnsFalseForOfflineNotificationRecipient() {
        assertFalse(handler.sendToUser(UUID.randomUUID(), notificationFrame()));
    }

    @Test
    void returnsFalseWhenNotificationSendFails() throws Exception {
        UUID userId = UUID.randomUUID();
        WebSocketSession session = session(userId, true);
        doThrow(new IOException("connection reset")).when(session).sendMessage(any(TextMessage.class));
        handler.afterConnectionEstablished(session);

        boolean delivered = handler.sendToUser(userId, notificationFrame());

        assertFalse(delivered);
    }

    @Test
    void alertSendFailureIsSwallowed() throws Exception {
        UUID userId = UUID.randomUUID();
        WebSocketSession session = session(userId, true);
        doThrow(new IOException("connection reset")).when(session).sendMessage(any(TextMessage.class));
        handler.afterConnectionEstablished(session);

        handler.sendToUser(userId, newAlertFrame());

        verify(session).sendMessage(any(TextMessage.class));
    }

    private static WsFrame newAlertFrame() {
        return WsFrame.alert(WsFrame.NEW_ALERT, new Payload("Road closure"));
    }

    private static WsFrame notificationFrame() {
        return WsFrame.notification(new Payload("Notice"));
    }

    private static WebSocketSession session(UUID userId, boolean open) {
        return session(userId, open, "session-" + userId);
    }

    /** Explicit session id, so two concurrent sessions for one user are distinguishable. */
    private static WebSocketSession session(UUID userId, boolean open, String sessionId) {
        WebSocketSession session = mock(WebSocketSession.class);
        Map<String, Object> attributes = new HashMap<>();
        attributes.put(JwtHandshakeInterceptor.USER_ID_ATTR, userId);
        when(session.getAttributes()).thenReturn(attributes);
        when(session.getId()).thenReturn(sessionId);
        when(session.isOpen()).thenReturn(open);
        return session;
    }

    private static TextMessage capturedMessage(WebSocketSession session) throws IOException {
        ArgumentCaptor<TextMessage> captor = ArgumentCaptor.forClass(TextMessage.class);
        verify(session).sendMessage(captor.capture());
        return captor.getValue();
    }
}
