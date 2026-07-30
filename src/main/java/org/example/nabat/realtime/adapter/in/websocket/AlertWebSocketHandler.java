package org.example.nabat.realtime.adapter.in.websocket;

import org.example.nabat.realtime.spi.WsBroadcaster;
import org.example.nabat.realtime.spi.WsFrame;
import org.example.nabat.realtime.spi.WsClusterRelay;
import org.example.nabat.realtime.spi.LocalWsDelivery;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pushes alert and notification frames to connected clients.
 *
 * <h2>Multiple sessions per user</h2>
 * Sessions are held as {@code userId -> (sessionId -> session)}. The previous
 * {@code Map<UUID, WebSocketSession>} allowed exactly one session per user, which
 * broke ordinary use: opening a second tab replaced the first session in the map,
 * and then closing the <em>first</em> tab removed the entry belonging to the
 * <em>second</em>, silently cutting off a client that was still connected.
 *
 * <h2>Concurrent sends</h2>
 * Sessions are wrapped in {@link ConcurrentWebSocketSessionDecorator}, because
 * frames originate from several threads at once — HTTP request threads handling
 * votes, and the Redis listener thread — and a raw {@code WebSocketSession} throws
 * {@code IllegalStateException} ("TEXT_PARTIAL_WRITING") if two threads write to it
 * concurrently.
 *
 * <h2>Payloads</h2>
 * This class does not know what a frame carries. Producing modules build their own
 * {@link WsFrame} from the same DTO their REST endpoints return, so the two transports
 * cannot drift, and hand it here through {@link WsBroadcaster}. Converting domain
 * records to DTOs in this class instead is what previously made realtime depend on the
 * incident and notification modules that were already depending on it.
 */
@Component
public class AlertWebSocketHandler extends TextWebSocketHandler
    implements LocalWsDelivery, WsBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(AlertWebSocketHandler.class);

    /**
     * Buffer allowance per session before a slow client is closed rather than allowed
     * to consume heap indefinitely.
     */
    private static final int SEND_BUFFER_LIMIT_BYTES = 512 * 1024;

    private final Map<UUID, Map<String, WebSocketSession>> sessionsByUser = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final WsClusterRelay clusterRelay;
    private final int sendTimeLimitMillis;

    public AlertWebSocketHandler(
        ObjectMapper objectMapper,
        WsClusterRelay clusterRelay,
        @Value("${nabat.websocket.send-time-limit-ms:5000}") int sendTimeLimitMillis
    ) {
        this.objectMapper = objectMapper;
        this.clusterRelay = clusterRelay;
        this.sendTimeLimitMillis = sendTimeLimitMillis;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        UUID userId = extractUserId(session);
        if (userId == null) {
            // The handshake interceptor should have rejected this; refuse to serve a
            // session we cannot attribute rather than holding it open.
            closeQuietly(session);
            return;
        }
        WebSocketSession guarded =
            new ConcurrentWebSocketSessionDecorator(session, sendTimeLimitMillis, SEND_BUFFER_LIMIT_BYTES);
        sessionsByUser
            .computeIfAbsent(userId, key -> new ConcurrentHashMap<>())
            .put(session.getId(), guarded);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UUID userId = extractUserId(session);
        if (userId == null) {
            return;
        }
        // Removed by session id, so closing one tab cannot evict another tab's session.
        sessionsByUser.computeIfPresent(userId, (key, sessions) -> {
            sessions.remove(session.getId());
            return sessions.isEmpty() ? null : sessions;
        });
    }

    @Override
    public boolean sendToUser(UUID userId, WsFrame frame) {
        if (deliverLocally(userId, frame)) {
            return true;
        }
        // Relay so a user connected to another replica still gets it in real time.
        // Without this, frames for users on other instances were dropped and only
        // surfaced on the next REST poll.
        clusterRelay.relayToUser(userId, frame);
        return false;
    }

    @Override
    public void broadcast(WsFrame frame) {
        deliverToAll(frame);
        // Peers ignore frames stamped with our own instance id, so our local clients are
        // not served a second copy when the message comes back round the channel.
        clusterRelay.relayBroadcast(frame);
    }

    @Override
    public boolean deliverLocally(UUID userId, WsFrame frame) {
        Collection<WebSocketSession> sessions = sessionsFor(userId);
        if (sessions.isEmpty()) {
            return false;
        }
        String json = serialize(frame);
        if (json == null) {
            return false;
        }

        boolean deliveredToAny = false;
        for (WebSocketSession session : sessions) {
            deliveredToAny |= write(session, json, frame.type());
        }
        return deliveredToAny;
    }

    @Override
    public void deliverToAll(WsFrame frame) {
        String json = serialize(frame);
        if (json == null) {
            return;
        }
        sessionsByUser.values().forEach(sessions ->
            sessions.values().forEach(session -> write(session, json, frame.type())));
    }

    /**
     * Whether {@code userId} has at least one open session here.
     *
     * <p>Not on {@link WsBroadcaster}: nothing in the application asks this question —
     * {@code NotificationSender} used to expose it and no caller ever appeared, so both
     * that method and the port declaration have been removed. It stays here because the
     * session bookkeeping this class does is worth asserting on directly.
     */
    public boolean isUserOnline(UUID userId) {
        return !sessionsFor(userId).isEmpty();
    }

    private Collection<WebSocketSession> sessionsFor(UUID userId) {
        Map<String, WebSocketSession> sessions = sessionsByUser.get(userId);
        if (sessions == null) {
            return Set.of();
        }
        return sessions.values().stream().filter(WebSocketSession::isOpen).toList();
    }

    private String serialize(WsFrame frame) {
        try {
            return objectMapper.writeValueAsString(frame.forClient());
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize {} frame: {}", frame.type(), e.getMessage());
            return null;
        }
    }

    private boolean write(WebSocketSession session, String json, String type) {
        if (!session.isOpen()) {
            return false;
        }
        try {
            session.sendMessage(new TextMessage(json));
            return true;
        } catch (IOException | IllegalStateException e) {
            // IllegalStateException: the decorator closes a session that exceeds its send
            // buffer or time limit, i.e. a client too slow to keep up.
            log.warn("Failed to deliver {} to session {}: {}", type, session.getId(), e.getMessage());
            return false;
        }
    }

    private UUID extractUserId(WebSocketSession session) {
        Object attr = session.getAttributes().get(JwtHandshakeInterceptor.USER_ID_ATTR);
        if (attr instanceof UUID uuid) {
            return uuid;
        }
        log.warn("WebSocket session {} has no authenticated userId attribute", session.getId());
        return null;
    }

    private void closeQuietly(WebSocketSession session) {
        try {
            session.close(CloseStatus.POLICY_VIOLATION);
        } catch (IOException e) {
            log.debug("Failed to close unauthenticated session {}: {}", session.getId(), e.getMessage());
        }
    }
}
