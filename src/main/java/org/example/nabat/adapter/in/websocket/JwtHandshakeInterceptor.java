package org.example.nabat.adapter.in.websocket;

import org.example.nabat.adapter.in.security.JwtTokenProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
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
 * Validates a JWT on the WebSocket upgrade request. The token is read from
 * either the {@code Authorization: Bearer ...} header or the {@code ?token=...}
 * query parameter (browsers cannot set headers on the WS handshake).
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

    public JwtHandshakeInterceptor(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Map<String, Object> attributes
    ) {
        String token = extractToken(request);
        if (token == null) {
            log.warn("WS handshake rejected: missing token");
            reject(response);
            return false;
        }
        if (!jwtTokenProvider.validateToken(token) || !jwtTokenProvider.isAccessToken(token)) {
            log.warn("WS handshake rejected: invalid or non-access token");
            reject(response);
            return false;
        }
        try {
            UUID userId = UUID.fromString(jwtTokenProvider.getUserIdFromToken(token));
            attributes.put(USER_ID_ATTR, userId);
            return true;
        } catch (IllegalArgumentException ex) {
            log.warn("WS handshake rejected: token has invalid userId claim");
            reject(response);
            return false;
        }
    }

    @Override
    public void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Exception exception
    ) {
        // no-op
    }

    private static String extractToken(ServerHttpRequest request) {
        String header = request.getHeaders().getFirst("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        if (request instanceof ServletServerHttpRequest servletRequest) {
            HttpServletRequest httpReq = servletRequest.getServletRequest();
            String token = httpReq.getParameter("token");
            if (token != null && !token.isBlank()) {
                return token;
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

