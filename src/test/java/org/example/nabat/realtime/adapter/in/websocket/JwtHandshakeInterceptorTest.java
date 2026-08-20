package org.example.nabat.realtime.adapter.in.websocket;

import org.example.nabat.identity.application.port.in.AuthenticateSessionUseCase;
import org.example.nabat.identity.domain.User;
import org.example.nabat.realtime.application.WebSocketTicketService;
import org.example.nabat.realtime.application.port.in.IssueWebSocketTicketUseCase;
import org.example.nabat.realtime.domain.WebSocketTicket;
import org.example.nabat.testsupport.FakeWebSocketTicketRepository;
import org.example.nabat.testsupport.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.socket.WebSocketHandler;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The handshake's own job: pick an auth path, apply the verdict, and put the user id
 * where the handler will find it.
 *
 * <p>What makes a token acceptable is {@link AuthenticateSessionUseCase}'s decision and
 * is mocked here — see {@code SessionAuthenticationServiceTest} for those rules. Ticket
 * single-use and expiry run against the real {@link WebSocketTicketService}, because
 * those are transport concerns and belong to this module.
 */
@ExtendWith(MockitoExtension.class)
class JwtHandshakeInterceptorTest {

    @Mock
    private AuthenticateSessionUseCase authenticateSessionUseCase;

    private FakeWebSocketTicketRepository ticketRepository;
    private WebSocketTicketService webSocketTicketService;
    private JwtHandshakeInterceptor interceptor;
    private User user;
    private final WebSocketHandler handler = mock(WebSocketHandler.class);

    @BeforeEach
    void setUp() {
        ticketRepository = new FakeWebSocketTicketRepository();
        webSocketTicketService = new WebSocketTicketService(ticketRepository, Duration.ofMinutes(2));
        interceptor = new JwtHandshakeInterceptor(authenticateSessionUseCase, webSocketTicketService);
        user = Fixtures.user();
    }

    @Test
    void rejectsAHandshakeCarryingNoCredential() {
        var servletResp = new MockHttpServletResponse();
        Map<String, Object> attrs = new HashMap<>();

        boolean ok = interceptor.beforeHandshake(
            request(null, null), new ServletServerHttpResponse(servletResp), handler, attrs);

        assertFalse(ok);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), servletResp.getStatus());
        assertFalse(attrs.containsKey(JwtHandshakeInterceptor.USER_ID_ATTR));
        verifyNoInteractions(authenticateSessionUseCase);
    }

    @Test
    void acceptsAnAccessTokenTheSessionRulesAccept() {
        when(authenticateSessionUseCase.authenticateAccessToken("good-token"))
            .thenReturn(Optional.of(user));
        Map<String, Object> attrs = new HashMap<>();

        boolean ok = interceptor.beforeHandshake(
            request("Bearer good-token", null),
            new ServletServerHttpResponse(new MockHttpServletResponse()),
            handler,
            attrs
        );

        assertTrue(ok);
        assertEquals(user.id().value(), attrs.get(JwtHandshakeInterceptor.USER_ID_ATTR));
    }

    /**
     * The revocation case that used to get through: the signature verifies, but the
     * session behind it is no longer accepted — a password reset, or a disabled account.
     * A socket opened here would outlive the token by hours.
     */
    @Test
    void rejectsAnAccessTokenTheSessionRulesRefuse() {
        when(authenticateSessionUseCase.authenticateAccessToken("revoked-token"))
            .thenReturn(Optional.empty());
        var servletResp = new MockHttpServletResponse();
        Map<String, Object> attrs = new HashMap<>();

        boolean ok = interceptor.beforeHandshake(
            request("Bearer revoked-token", null),
            new ServletServerHttpResponse(servletResp),
            handler,
            attrs
        );

        assertFalse(ok);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), servletResp.getStatus());
        assertFalse(attrs.containsKey(JwtHandshakeInterceptor.USER_ID_ATTR));
    }

    @Test
    void acceptsAValidTicketInTheQueryParam() {
        when(authenticateSessionUseCase.resolveActiveUser(user.id())).thenReturn(Optional.of(user));
        Map<String, Object> attrs = new HashMap<>();

        boolean ok = interceptor.beforeHandshake(
            request(null, issueTicket()),
            new ServletServerHttpResponse(new MockHttpServletResponse()),
            handler,
            attrs
        );

        assertTrue(ok);
        assertEquals(user.id().value(), attrs.get(JwtHandshakeInterceptor.USER_ID_ATTR));
    }

    /**
     * A ticket proves its holder was authenticated when it was issued, not that the
     * account is still usable now — the whole ticket lifetime sits after that check.
     */
    @Test
    void rejectsATicketWhoseOwnerIsNoLongerAnActiveAccount() {
        when(authenticateSessionUseCase.resolveActiveUser(user.id())).thenReturn(Optional.empty());
        var servletResp = new MockHttpServletResponse();
        Map<String, Object> attrs = new HashMap<>();

        boolean ok = interceptor.beforeHandshake(
            request(null, issueTicket()),
            new ServletServerHttpResponse(servletResp),
            handler,
            attrs
        );

        assertFalse(ok);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), servletResp.getStatus());
        assertFalse(attrs.containsKey(JwtHandshakeInterceptor.USER_ID_ATTR));
    }

    @Test
    void rejectsAReusedTicket() {
        when(authenticateSessionUseCase.resolveActiveUser(user.id())).thenReturn(Optional.of(user));
        String ticket = issueTicket();

        assertTrue(interceptor.beforeHandshake(
            request(null, ticket),
            new ServletServerHttpResponse(new MockHttpServletResponse()),
            handler,
            new HashMap<>()
        ));

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
    void rejectsAnExpiredTicket() {
        ticketRepository.save(
            new WebSocketTicket("expired-ticket", user.id(), Instant.now().minusSeconds(10)));
        var servletResp = new MockHttpServletResponse();

        boolean ok = interceptor.beforeHandshake(
            request(null, "expired-ticket"),
            new ServletServerHttpResponse(servletResp),
            handler,
            new HashMap<>()
        );

        assertFalse(ok);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), servletResp.getStatus());
        verifyNoInteractions(authenticateSessionUseCase);
    }

    /**
     * {@code ?token=<jwt>} was the original scheme. It is not read any more — a JWT in a
     * URL lands in access logs and browser history — and must not quietly still work.
     */
    @Test
    void ignoresALegacyJwtQueryParameter() {
        MockHttpServletRequest httpReq = new MockHttpServletRequest("GET", "/ws/alerts");
        httpReq.setParameter("token", "some-access-token");
        var servletResp = new MockHttpServletResponse();

        boolean ok = interceptor.beforeHandshake(
            new ServletServerHttpRequest(httpReq),
            new ServletServerHttpResponse(servletResp),
            handler,
            new HashMap<>()
        );

        assertFalse(ok);
        assertEquals(HttpStatus.UNAUTHORIZED.value(), servletResp.getStatus());
        verifyNoInteractions(authenticateSessionUseCase);
    }

    private String issueTicket() {
        return webSocketTicketService.issueTicket(
            new IssueWebSocketTicketUseCase.IssueWebSocketTicketCommand(user.id())
        ).ticket();
    }

    private static ServerHttpRequest request(String authHeader, String queryTicket) {
        MockHttpServletRequest httpReq = new MockHttpServletRequest("GET", "/ws/alerts");
        if (authHeader != null) {
            httpReq.addHeader("Authorization", authHeader);
        }
        if (queryTicket != null) {
            httpReq.setParameter("ticket", queryTicket);
        }
        return new ServletServerHttpRequest(httpReq);
    }
}
