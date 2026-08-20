package org.example.nabat.identity.application;

import org.example.nabat.identity.adapter.in.security.JwtTokenProvider;
import org.example.nabat.identity.application.port.out.UserRepository;
import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.testsupport.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The single set of rules for accepting a credential, exercised with a real
 * {@link JwtTokenProvider} so the token shapes are the ones production mints.
 *
 * <p>Both the HTTP filter and the WebSocket handshake come through here. The
 * handshake used to answer these questions with a signature check of its own, which
 * is why revocation and the disabled flag are asserted at this level rather than in
 * either caller's test.
 */
@ExtendWith(MockitoExtension.class)
class SessionAuthenticationServiceTest {

    private static final String SECRET =
        "session-auth-secret-key-min-256-bits-for-testing-purposes-only-not-prod";

    @Mock
    private UserRepository userRepository;

    private JwtTokenProvider tokenProvider;
    private SessionAuthenticationService service;
    private User user;

    @BeforeEach
    void setUp() {
        tokenProvider = new JwtTokenProvider(SECRET, 3_600_000L, 86_400_000L);
        service = new SessionAuthenticationService(tokenProvider, userRepository);
        user = Fixtures.user();
    }

    @Test
    void acceptsAValidAccessTokenForAnEnabledUser() {
        when(userRepository.findById(user.id())).thenReturn(Optional.of(user));

        Optional<User> authenticated =
            service.authenticateAccessToken(tokenProvider.generateAccessToken(user));

        assertTrue(authenticated.isPresent());
        assertSame(user, authenticated.get());
    }

    @Test
    void rejectsARefreshTokenPresentedAsAnAccessToken() {
        assertTrue(service.authenticateAccessToken(tokenProvider.generateRefreshToken(user)).isEmpty());
        verifyNoInteractions(userRepository);
    }

    @Test
    void rejectsAnUnparseableTokenWithoutALookup() {
        assertTrue(service.authenticateAccessToken("not-a-jwt").isEmpty());
        verifyNoInteractions(userRepository);
    }

    @Test
    void rejectsAnAbsentTokenWithoutALookup() {
        assertTrue(service.authenticateAccessToken(null).isEmpty());
        assertTrue(service.authenticateAccessToken("  ").isEmpty());
        verifyNoInteractions(userRepository);
    }

    @Test
    void rejectsATokenNamingAUserThatNoLongerExists() {
        when(userRepository.findById(user.id())).thenReturn(Optional.empty());

        assertTrue(service.authenticateAccessToken(tokenProvider.generateAccessToken(user)).isEmpty());
    }

    @Test
    void rejectsATokenForADisabledUser() {
        String token = tokenProvider.generateAccessToken(user);
        when(userRepository.findById(user.id())).thenReturn(Optional.of(user.disable()));

        assertTrue(service.authenticateAccessToken(token).isEmpty());
    }

    /**
     * A password reset bumps {@code tokenVersion}, which has to invalidate tokens
     * already in flight — they are still inside their expiry window, so nothing else
     * would stop them.
     */
    @Test
    void rejectsATokenInvalidatedByACredentialChange() {
        String tokenMintedBeforeTheReset = tokenProvider.generateAccessToken(user);
        when(userRepository.findById(user.id())).thenReturn(Optional.of(user.invalidateSessions()));

        assertTrue(service.authenticateAccessToken(tokenMintedBeforeTheReset).isEmpty());
    }

    @Test
    void resolvesAnEnabledUserByIdForCredentialsThatCarryNoTokenVersion() {
        when(userRepository.findById(user.id())).thenReturn(Optional.of(user));

        assertEquals(Optional.of(user), service.resolveActiveUser(user.id()));
    }

    @Test
    void refusesToResolveADisabledOrUnknownUser() {
        UserId unknown = UserId.generate();
        when(userRepository.findById(user.id())).thenReturn(Optional.of(user.disable()));
        when(userRepository.findById(unknown)).thenReturn(Optional.empty());

        assertTrue(service.resolveActiveUser(user.id()).isEmpty());
        assertTrue(service.resolveActiveUser(unknown).isEmpty());
        assertTrue(service.resolveActiveUser(null).isEmpty());
    }
}
