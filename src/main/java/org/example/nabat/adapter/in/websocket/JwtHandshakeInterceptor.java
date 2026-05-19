package org.example.nabat.adapter.in.websocket;

import org.example.nabat.adapter.in.security.JwtTokenProvider;
import org.example.nabat.application.port.in.RedeemWebSocketTicketUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.lang.NonNull;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;

/**
 * Validates WebSocket authentication on the HTTP upgrade request.
 *
 * <p>Non-browser clients may send an access token in the
 * {@code Authorization: Bearer ...} header. Browser clients should first
 * obtain a short-lived one-time ticket over the authenticated REST API and
 * then connect with {@code ?ticket=...}.
 *
 * <p>On success the authenticated user id is placed into the session
 * attributes under {@link #USER_ID_ATTR} so handlers can read it without
 * trusting client-supplied data.
 */
@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    public static final String USER_ID_ATTR = "userId";

    private static final Logger log = LoggerFactory.getLogger(JwtHandshakeInterceptor.class);

    private final JwtTokenProvider jwtTokenProvider;
    private final RedeemWebSocketTicketUseCase redeemWebSocketTicketUseCase;

    public JwtHandshakeInterceptor(
        JwtTokenProvider jwtTokenProvider,
        RedeemWebSocketTicketUseCase redeemWebSocketTicketUseCase
    ) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.redeemWebSocketTicketUseCase = redeemWebSocketTicketUseCase;
    }

    @Override
    public boolean beforeHandshake(
        @NonNull ServerHttpRequest request,
        @NonNull ServerHttpResponse response,
        @NonNull WebSocketHandler wsHandler,
        @NonNull Map<String, Object> attributes
    ) {
        UUID userId = authenticate(request);
        if (userId == null) {
            reject(response);
            return false;
        }

        attributes.put(USER_ID_ATTR, userId);
        return true;
    }

    @Override
    public void afterHandshake(
        @NonNull ServerHttpRequest request,
        @NonNull ServerHttpResponse response,
        @NonNull WebSocketHandler wsHandler,
        Exception exception
    ) {
        // no-op
    }

    private UUID authenticate(ServerHttpRequest request) {
        String accessToken = extractBearerToken(request);
        if (accessToken != null) {
            return authenticateAccessToken(accessToken);
        }

        String ticket = extractTicket(request);
        if (ticket != null) {
            return redeemTicket(ticket);
        }

        log.warn("WS handshake rejected: missing Authorization header or ticket");
        return null;
    }

    private UUID authenticateAccessToken(String token) {
        if (!jwtTokenProvider.validateToken(token) || !jwtTokenProvider.isAccessToken(token)) {
            log.warn("WS handshake rejected: invalid or non-access token");
            return null;
        }

        try {
            return UUID.fromString(jwtTokenProvider.getUserIdFromToken(token));
        } catch (IllegalArgumentException ex) {
            log.warn("WS handshake rejected: token has invalid userId claim");
            return null;
        }
    }

    private UUID redeemTicket(String ticket) {
        try {
            return redeemWebSocketTicketUseCase.redeem(ticket).value();
        } catch (RuntimeException ex) {
            log.warn("WS handshake rejected: invalid or expired ticket");
            return null;
        }
    }

    private static String extractBearerToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }

    private static String extractTicket(ServerHttpRequest request) {
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpReq = servletRequest.getServletRequest();
            String ticket = httpReq.getParameter("ticket");
            if (ticket != null && !ticket.isBlank()) {
                return ticket;
            }
        }
        return null;
    }

    private static void reject(ServerHttpResponse response) {
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        if (response instanceof ServletServerHttpResponse s) {
            s.getServletResponse().setStatus(HttpStatus.UNAUTHORIZED.value());
        }
    }
}

