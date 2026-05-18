package org.example.nabat.adapter.in.security;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.HttpServletResponse;
import org.example.nabat.application.port.out.UserRepository;
import org.example.nabat.domain.model.Role;
import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.UserId;
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

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    private static final String SECRET =
        "filter-secret-key-min-256-bits-for-testing-purposes-only-not-for-prod";

    @Mock
    private UserRepository userRepository;

    private JwtTokenProvider tokenProvider;
    private JwtAuthenticationFilter filter;
    private User user;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(SECRET, 3_600_000L, 86_400_000L);
        filter = new JwtAuthenticationFilter(tokenProvider, userRepository);
        user = new User(
            UserId.generate(),
            "filter@example.com",
            "hash",
            "Filter User",
            Role.ADMIN,
            true,
            false,
            Instant.now(),
            Instant.now()
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authenticatesEnabledUserWithValidAccessToken() throws Exception {
        String token = tokenProvider.generateAccessToken(user);
        when(userRepository.findById(user.id())).thenReturn(Optional.of(user));

        filter.doFilterInternal(requestWithBearer(token), new MockHttpServletResponse(), new MockFilterChain());

        var authentication = SecurityContextHolder.getContext().getAuthentication();
        assertInstanceOf(UsernamePasswordAuthenticationToken.class, authentication);
        assertSame(user, authentication.getPrincipal());
        assertTrue(authentication.getAuthorities().stream()
            .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")));
        verify(userRepository).findById(user.id());
    }

    @Test
    void rejectsRefreshTokenForApiAuthentication() throws Exception {
        String token = tokenProvider.generateRefreshToken(user);

        filter.doFilterInternal(requestWithBearer(token), new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userRepository);
    }

    @Test
    void invalidTokenContinuesUnauthenticatedWithoutRepositoryLookup() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(
            requestWithBearer("not-a-jwt"),
            response,
            (request, servletResponse) -> ((HttpServletResponse) servletResponse).setStatus(HttpServletResponse.SC_NO_CONTENT)
        );

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(HttpServletResponse.SC_NO_CONTENT, response.getStatus());
        verifyNoInteractions(userRepository);
    }

    @Test
    void disabledUserIsNotAuthenticated() throws Exception {
        String token = tokenProvider.generateAccessToken(user);
        when(userRepository.findById(user.id())).thenReturn(Optional.of(user.disable()));

        filter.doFilterInternal(requestWithBearer(token), new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userRepository).findById(user.id());
    }

    @Test
    void missingUserIsNotAuthenticated() throws Exception {
        String token = tokenProvider.generateAccessToken(user);
        when(userRepository.findById(user.id())).thenReturn(Optional.empty());

        filter.doFilterInternal(requestWithBearer(token), new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(userRepository).findById(user.id());
    }

    @Test
    void malformedUserIdClaimClearsPartialSecurityContext() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken("previous", null)
        );
        String token = tokenWithInvalidUserIdClaim();

        filter.doFilterInternal(requestWithBearer(token), new MockHttpServletResponse(), new MockFilterChain());

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verifyNoInteractions(userRepository);
    }

    @Test
    void missingBearerHeaderDoesNotAuthenticate() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(
            request,
            response,
            (req, servletResponse) -> ((HttpServletResponse) servletResponse).setStatus(HttpServletResponse.SC_NO_CONTENT)
        );

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(HttpServletResponse.SC_NO_CONTENT, response.getStatus());
        verifyNoInteractions(userRepository);
    }

    private static MockHttpServletRequest requestWithBearer(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + token);
        return request;
    }

    private static String tokenWithInvalidUserIdClaim() {
        return Jwts.builder()
            .claims(Map.of(
                "userId", "not-a-uuid",
                "email", "bad@example.com",
                "role", "USER",
                JwtTokenProvider.TOKEN_TYPE, JwtTokenProvider.ACCESS_TOKEN_TYPE
            ))
            .subject("bad@example.com")
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + 3_600_000L))
            .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8)))
            .compact();
    }
}


