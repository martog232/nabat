package org.example.nabat.adapter.in.websocket;

import org.example.nabat.adapter.in.security.JwtTokenProvider;
import org.example.nabat.domain.model.Role;
import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class JwtHandshakeInterceptorTest {

    private static final String SECRET =
        "ws-handshake-secret-key-min-256-bits-for-testing-purposes-only-not-for-prod";

    private JwtTokenProvider tokenProvider;
    private JwtHandshakeInterceptor interceptor;
    private User user;
    private final WebSocketHandler handler = mock(WebSocketHandler.class);

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(SECRET, 3_600_000L, 86_400_000L);
        interceptor = new JwtHandshakeInterceptor(tokenProvider);
        user = new User(
            UserId.of(UUID.randomUUID()), "ws@example.com", "h", "WS User",
            Role.USER, true, Instant.now(), Instant.now()
        );
    }

    @Test
    void rejectsMissingToken() {
        var req = request(null, null);
        var servletResp = new MockHttpServletResponse();
        var resp = new ServletServerHttpResponse(servletResp);
        Map<String, Object> attrs = new HashMap<>();

        boolean ok = interceptor.beforeHandshake(req, resp, handler, attrs);

        assertFalse(ok);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), servletResp.getStatus());
        assertFalse(attrs.containsKey(JwtHandshakeInterceptor.USER_ID_ATTR));
    }

    @Test
    void rejectsBadToken() {
        var req = request("Bearer not-a-jwt", null);
        var servletResp = new MockHttpServletResponse();
        var resp = new ServletServerHttpResponse(servletResp);
        Map<String, Object> attrs = new HashMap<>();

        boolean ok = interceptor.beforeHandshake(req, resp, handler, attrs);

        assertFalse(ok);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), servletResp.getStatus());
    }

    @Test
    void rejectsRefreshToken() {
        String refresh = tokenProvider.generateRefreshToken(user);
        var req = request("Bearer " + refresh, null);
        var servletResp = new MockHttpServletResponse();
        var resp = new ServletServerHttpResponse(servletResp);
        Map<String, Object> attrs = new HashMap<>();

        boolean ok = interceptor.beforeHandshake(req, resp, handler, attrs);

        assertFalse(ok);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), servletResp.getStatus());
    }

    @Test
    void acceptsValidAccessTokenInAuthorizationHeader() {
        String access = tokenProvider.generateAccessToken(user);
        var req = request("Bearer " + access, null);
        var resp = new ServletServerHttpResponse(new MockHttpServletResponse());
        Map<String, Object> attrs = new HashMap<>();

        boolean ok = interceptor.beforeHandshake(req, resp, handler, attrs);

        assertTrue(ok);
        assertEquals(user.id().value(), attrs.get(JwtHandshakeInterceptor.USER_ID_ATTR));
    }

    @Test
    void acceptsValidAccessTokenInQueryParam() {
        String access = tokenProvider.generateAccessToken(user);
        var req = request(null, access);
        var resp = new ServletServerHttpResponse(new MockHttpServletResponse());
        Map<String, Object> attrs = new HashMap<>();

        boolean ok = interceptor.beforeHandshake(req, resp, handler, attrs);

        assertTrue(ok);
        assertEquals(user.id().value(), attrs.get(JwtHandshakeInterceptor.USER_ID_ATTR));
    }

    private static ServerHttpRequest request(String authHeader, String queryToken) {
        MockHttpServletRequest httpReq = new MockHttpServletRequest("GET", "/ws/alerts");
        if (authHeader != null) httpReq.addHeader("Authorization", authHeader);
        if (queryToken != null) httpReq.setParameter("token", queryToken);
        return new ServletServerHttpRequest(httpReq);
    }
}




