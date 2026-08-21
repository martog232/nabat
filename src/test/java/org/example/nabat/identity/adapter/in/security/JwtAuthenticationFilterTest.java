package org.example.nabat.identity.adapter.in.security;

import jakarta.servlet.http.HttpServletResponse;
import org.example.nabat.identity.application.port.in.AuthenticateSessionUseCase;
import org.example.nabat.identity.domain.Role;
import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * What the filter itself is responsible for: turning an accepted session into a
 * populated security context, and leaving the context empty otherwise.
 *
 * <p>Whether a token names an accepted session is
 * {@link AuthenticateSessionUseCase}'s decision, and is covered by
 * {@code SessionAuthenticationServiceTest} — the WebSocket handshake asks the same
 * question, so the rules cannot live here.
 */
@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private AuthenticateSessionUseCase authenticateSessionUseCase;

    private JwtAuthenticationFilter filter;
    private User user;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthenticationFilter(authenticateSessionUseCase);
        user = new User(
            UserId.generate(),
            "filter@example.com",
            "hash",
            "Filter User",
            Role.ADMIN,
            true,
            false,
            Instant.now(),
            Instant.now(),
            5,
            null,
            null,
            null,
            0
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesAnAcceptedSessionWithItsRole() throws Exception {
        when(authenticateSessionUseCase.authenticateAccessToken("good-token"))
            .thenReturn(Optional.of(user));

        filter.doFilterInternal(
            requestWithBearer("good-token"), new MockHttpServletResponse(), new MockFilterChain());

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertInstanceOf(UsernamePasswordAuthenticationToken.class, authentication);
        assertSame(user, authentication.getPrincipal());
        assertTrue(authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
    }

    @Test
    void rejectedTokenContinuesTheChainUnauthenticated() throws Exception {
        when(authenticateSessionUseCase.authenticateAccessToken("stale-token"))
            .thenReturn(Optional.empty());
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(
            requestWithBearer("stale-token"),
            response,
            (request, servletResponse) ->
                ((HttpServletResponse) servletResponse).setStatus(HttpServletResponse.SC_NO_CONTENT)
        );

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(HttpServletResponse.SC_NO_CONTENT, response.getStatus());
    }

    /**
     * A request carrying a bearer token must not inherit an authentication that was
     * already in the context. The chain is stateless so that is normally impossible;
     * the filter clears it anyway, and this pins that.
     */
    @Test
    void rejectedTokenClearsAPreExistingAuthentication() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("previous", null)
        );
        when(authenticateSessionUseCase.authenticateAccessToken("stale-token"))
            .thenReturn(Optional.empty());

        filter.doFilterInternal(
            requestWithBearer("stale-token"), new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test
    void missingBearerHeaderIsNotEvenAsked() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(
            new MockHttpServletRequest(),
            response,
            (req, servletResponse) ->
                ((HttpServletResponse) servletResponse).setStatus(HttpServletResponse.SC_NO_CONTENT)
        );

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(HttpServletResponse.SC_NO_CONTENT, response.getStatus());
        verifyNoInteractions(authenticateSessionUseCase);
    }

    private static MockHttpServletRequest requestWithBearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }
}
