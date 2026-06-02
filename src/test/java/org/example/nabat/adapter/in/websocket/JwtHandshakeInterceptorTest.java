package org.example.nabat.adapter.in.websocket;

import org.example.nabat.adapter.in.security.JwtTokenProvider;
import org.example.nabat.adapter.out.memory.InMemoryWebSocketTicketRepository;
import org.example.nabat.application.port.in.IssueWebSocketTicketUseCase;
import org.example.nabat.application.service.WebSocketTicketService;
import org.example.nabat.domain.model.Role;
import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.domain.model.WebSocketTicket;
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
    private InMemoryWebSocketTicketRepository ticketRepository;
    private WebSocketTicketService webSocketTicketService;
    private JwtHandshakeInterceptor interceptor;
    private User user;
    private final WebSocketHandler handler = mock(WebSocketHandler.class);

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(SECRET, 3_600_000L, 86_400_000L);
        ticketRepository = new InMemoryWebSocketTicketRepository();
        webSocketTicketService = new WebSocketTicketService(ticketRepository, java.time.Duration.ofMinutes(2));
        interceptor = new JwtHandshakeInterceptor(tokenProvider, webSocketTicketService);
        user = new User(
            UserId.of(UUID.randomUUID()), "ws@example.com", "h", "WS User",
            Role.USER, true, false, Instant.now(), Instant.now(), 5, null, null, null
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
    void acceptsValidTicketInQueryParam() {
        String ticket = webSocketTicketService.issueTicket(
            new IssueWebSocketTicketUseCase.IssueWebSocketTicketCommand(user.id())
        ).ticket();
        var req = request(null, ticket);
        var resp = new ServletServerHttpResponse(new MockHttpServletResponse());
        Map<String, Object> attrs = new HashMap<>();

        boolean ok = interceptor.beforeHandshake(req, resp, handler, attrs);

        assertTrue(ok);
        assertEquals(user.id().value(), attrs.get(JwtHandshakeInterceptor.USER_ID_ATTR));
    }

    @Test
    void rejectsReusedTicket() {
        String ticket = webSocketTicketService.issueTicket(
            new IssueWebSocketTicketUseCase.IssueWebSocketTicketCommand(user.id())
        ).ticket();
        var firstResponse = new ServletServerHttpResponse(new MockHttpServletResponse());

        assertTrue(interceptor.beforeHandshake(request(null, ticket), firstResponse, handler, new HashMap<>()));

        var secondServletResp = new MockHttpServletResponse();
        boolean ok = interceptor.beforeHandshake(
            request(null, ticket),
            new ServletServerHttpResponse(secondServletResp),
            handler,
            new HashMap<>()
        );

        assertFalse(ok);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), secondServletResp.getStatus());
    }

    @Test
    void rejectsExpiredTicket() {
        ticketRepository.save(new WebSocketTicket("expired-ticket", user.id(), Instant.now().minusSeconds(10)));
        var servletResp = new MockHttpServletResponse();
        var resp = new ServletServerHttpResponse(servletResp);

        boolean ok = interceptor.beforeHandshake(request(null, "expired-ticket"), resp, handler, new HashMap<>());

        assertFalse(ok);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), servletResp.getStatus());
    }

    @Test
    void rejectsLegacyJwtQueryParameter() {
        String access = tokenProvider.generateAccessToken(user);
        MockHttpServletRequest httpReq = new MockHttpServletRequest("GET", "/ws/alerts");
        httpReq.setParameter("token", access);
        ServerHttpRequest req = new ServletServerHttpRequest(httpReq);
        var servletResp = new MockHttpServletResponse();

        boolean ok = interceptor.beforeHandshake(
            req,
            new ServletServerHttpResponse(servletResp),
            handler,
            new HashMap<>()
        );

        assertFalse(ok);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), servletResp.getStatus());
    }

    private static ServerHttpRequest request(String authHeader, String queryTicket) {
        MockHttpServletRequest httpReq = new MockHttpServletRequest("GET", "/ws/alerts");
        if (authHeader != null) httpReq.addHeader("Authorization", authHeader);
        if (queryTicket != null) httpReq.setParameter("ticket", queryTicket);
        return new ServletServerHttpRequest(httpReq);
    }
}



