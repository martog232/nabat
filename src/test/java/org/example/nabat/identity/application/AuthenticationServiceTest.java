package org.example.nabat.identity.application;

import org.example.nabat.identity.application.port.in.LoginUserUseCase;
import org.example.nabat.identity.application.port.in.RefreshTokenUseCase;
import org.example.nabat.identity.application.port.in.RegisterUserUseCase;
import org.example.nabat.identity.application.port.out.LoginAttemptPort;
import org.example.nabat.identity.application.port.out.RefreshTokenStore;
import org.example.nabat.identity.application.port.out.RequestContextPort;
import org.example.nabat.identity.application.port.out.TokenProvider;
import org.example.nabat.identity.application.port.out.UserRepository;
import org.example.nabat.identity.domain.AuthenticationFailedException;
import org.example.nabat.identity.domain.EmailAlreadyRegisteredException;
import org.example.nabat.identity.domain.Role;
import org.example.nabat.identity.domain.User;
import org.example.nabat.testsupport.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenProvider tokenProvider;

    @Mock
    private RefreshTokenStore refreshTokenStore;

    @Mock
    private LoginAttemptPort loginAttempts;

    @Mock
    private RequestContextPort requestContext;

    private PasswordEncoder passwordEncoder;
    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authenticationService = new AuthenticationService(
            userRepository, passwordEncoder, tokenProvider, refreshTokenStore, loginAttempts, requestContext);
        when(requestContext.clientIp()).thenReturn("203.0.113.7");
    }

    @Test
    void shouldRegisterNewUserAndIssueSession() {
        RegisterUserUseCase.RegisterCommand command =
            new RegisterUserUseCase.RegisterCommand("test@example.com", "password123", "Test User");

        when(userRepository.existsByEmail(command.email())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tokenProvider.generateAccessToken(any())).thenReturn("access-token");
        when(tokenProvider.generateRefreshToken(any())).thenReturn("refresh-token");
        when(tokenProvider.getJwtExpiration()).thenReturn(3600000L);

        RegisterUserUseCase.RegistrationResult result = authenticationService.register(command);

        assertNotNull(result);
        assertEquals(command.email(), result.user().email());
        assertEquals(command.displayName(), result.user().displayName());
        assertEquals(Role.USER, result.user().role());
        assertTrue(result.user().enabled());
        // Registration issues the session itself rather than replaying the password
        // through login().
        assertEquals("access-token", result.accessToken());
        assertEquals("refresh-token", result.refreshToken());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldRejectDuplicateEmailAsConflict() {
        RegisterUserUseCase.RegisterCommand command =
            new RegisterUserUseCase.RegisterCommand("test@example.com", "password123", "Test User");

        when(userRepository.existsByEmail(command.email())).thenReturn(true);

        // Was IllegalArgumentException (→ 400) carrying a success-sounding message.
        assertThrows(EmailAlreadyRegisteredException.class, () -> authenticationService.register(command));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldLoginWithValidCredentials() {
        String rawPassword = "password123";
        User user = Fixtures.user().toBuilder()
            .password(passwordEncoder.encode(rawPassword))
            .build();

        LoginUserUseCase.LoginCommand command =
            new LoginUserUseCase.LoginCommand(user.email(), rawPassword);

        when(userRepository.findByEmail(command.email())).thenReturn(Optional.of(user));
        when(tokenProvider.generateAccessToken(user)).thenReturn("access-token");
        when(tokenProvider.generateRefreshToken(user)).thenReturn("refresh-token");
        when(tokenProvider.getJwtExpiration()).thenReturn(3600000L);

        LoginUserUseCase.LoginResult result = authenticationService.login(command);

        assertEquals("access-token", result.accessToken());
        assertEquals("refresh-token", result.refreshToken());
        assertEquals(user, result.user());
        verify(loginAttempts).recordSuccess(command.email(), "203.0.113.7");
    }

    @Test
    void shouldNotLoginWithUnknownEmail() {
        LoginUserUseCase.LoginCommand command =
            new LoginUserUseCase.LoginCommand("nonexistent@example.com", "password123");

        when(userRepository.findByEmail(command.email())).thenReturn(Optional.empty());

        assertThrows(AuthenticationFailedException.class, () -> authenticationService.login(command));
        verify(loginAttempts).recordFailure(command.email(), "203.0.113.7");
    }

    @Test
    void shouldNotLoginWithInvalidPassword() {
        User user = Fixtures.user().toBuilder()
            .password(passwordEncoder.encode("correctPassword"))
            .build();

        LoginUserUseCase.LoginCommand command =
            new LoginUserUseCase.LoginCommand(user.email(), "wrongPassword");

        when(userRepository.findByEmail(command.email())).thenReturn(Optional.of(user));

        assertThrows(AuthenticationFailedException.class, () -> authenticationService.login(command));
        verify(loginAttempts).recordFailure(command.email(), "203.0.113.7");
    }

    @Test
    void shouldNotLoginDisabledUser() {
        String rawPassword = "password123";
        User user = Fixtures.user().toBuilder()
            .password(passwordEncoder.encode(rawPassword))
            .enabled(false)
            .build();

        LoginUserUseCase.LoginCommand command =
            new LoginUserUseCase.LoginCommand(user.email(), rawPassword);

        when(userRepository.findByEmail(command.email())).thenReturn(Optional.of(user));

        assertThrows(AuthenticationFailedException.class, () -> authenticationService.login(command));
    }

    @Test
    void shouldRefreshTokensAndConsumeThePresentedToken() {
        User user = Fixtures.user();
        String refreshToken = "valid-refresh-token";

        when(tokenProvider.parseRefreshToken(refreshToken)).thenReturn(Optional.of(
            new TokenProvider.RefreshTokenClaims(user.id().value(), "jti-1", user.tokenVersion())));
        when(userRepository.findById(user.id())).thenReturn(Optional.of(user));
        when(tokenProvider.refreshTokenLifetime()).thenReturn(Duration.ofDays(7));
        when(refreshTokenStore.consume("jti-1", Duration.ofDays(7))).thenReturn(true);
        when(tokenProvider.generateAccessToken(user)).thenReturn("new-access-token");
        when(tokenProvider.generateRefreshToken(user)).thenReturn("new-refresh-token");
        when(tokenProvider.getJwtExpiration()).thenReturn(3600000L);

        RefreshTokenUseCase.AuthTokens result = authenticationService.refresh(refreshToken);

        assertEquals("new-access-token", result.accessToken());
        assertEquals("new-refresh-token", result.refreshToken());
        verify(refreshTokenStore).consume("jti-1", Duration.ofDays(7));
    }

    @Test
    void shouldRejectReplayedRefreshToken() {
        User user = Fixtures.user();
        String refreshToken = "already-used";

        when(tokenProvider.parseRefreshToken(refreshToken)).thenReturn(Optional.of(
            new TokenProvider.RefreshTokenClaims(user.id().value(), "jti-used", user.tokenVersion())));
        when(userRepository.findById(user.id())).thenReturn(Optional.of(user));
        when(tokenProvider.refreshTokenLifetime()).thenReturn(Duration.ofDays(7));
        // Already exchanged once.
        when(refreshTokenStore.consume(anyString(), any())).thenReturn(false);

        assertThrows(AuthenticationFailedException.class, () -> authenticationService.refresh(refreshToken));
        verify(tokenProvider, never()).generateAccessToken(any());
    }

    @Test
    void shouldRejectRefreshTokenInvalidatedByPasswordChange() {
        User user = Fixtures.user().toBuilder().tokenVersion(3).build();
        String refreshToken = "stale-version";

        when(tokenProvider.parseRefreshToken(refreshToken)).thenReturn(Optional.of(
            // Minted before the credential change bumped the version to 3.
            new TokenProvider.RefreshTokenClaims(user.id().value(), "jti-2", 2)));
        when(userRepository.findById(user.id())).thenReturn(Optional.of(user));

        assertThrows(AuthenticationFailedException.class, () -> authenticationService.refresh(refreshToken));
        verify(refreshTokenStore, never()).consume(anyString(), any());
    }

    @Test
    void shouldNotRefreshUnparseableToken() {
        when(tokenProvider.parseRefreshToken("invalid-token")).thenReturn(Optional.empty());

        assertThrows(AuthenticationFailedException.class, () -> authenticationService.refresh("invalid-token"));
    }

    @Test
    void shouldNotRefreshWhenUserNoLongerExists() {
        UUID userId = UUID.randomUUID();
        when(tokenProvider.parseRefreshToken("orphan")).thenReturn(Optional.of(
            new TokenProvider.RefreshTokenClaims(userId, "jti-3", 0)));
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(AuthenticationFailedException.class, () -> authenticationService.refresh("orphan"));
    }
}
