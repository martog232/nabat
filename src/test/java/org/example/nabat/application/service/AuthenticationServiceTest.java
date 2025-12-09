package org.example.nabat.application.service;

import org.example.nabat.application.port.in.LoginUserUseCase;
import org.example.nabat.application.port.in.RefreshTokenUseCase;
import org.example.nabat.application.port.in.RegisterUserUseCase;
import org.example.nabat.application.port.out.TokenProvider;
import org.example.nabat.application.port.out.UserRepository;
import org.example.nabat.domain.model.Role;
import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthenticationServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenProvider tokenProvider;

    private PasswordEncoder passwordEncoder;
    private AuthenticationService authenticationService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authenticationService = new AuthenticationService(userRepository, passwordEncoder, tokenProvider);
    }

    @Test
    void shouldRegisterNewUser() {
        RegisterUserUseCase.RegisterCommand command = 
            new RegisterUserUseCase.RegisterCommand("test@example.com", "password123", "Test User");
        
        when(userRepository.existsByEmail(command.email())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User result = authenticationService.register(command);

        assertNotNull(result);
        assertEquals(command.email(), result.email());
        assertEquals(command.displayName(), result.displayName());
        assertEquals(Role.USER, result.role());
        assertTrue(result.enabled());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldNotRegisterUserWithExistingEmail() {
        RegisterUserUseCase.RegisterCommand command = 
            new RegisterUserUseCase.RegisterCommand("test@example.com", "password123", "Test User");
        
        when(userRepository.existsByEmail(command.email())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> authenticationService.register(command));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void shouldLoginWithValidCredentials() {
        String rawPassword = "password123";
        String hashedPassword = passwordEncoder.encode(rawPassword);
        
        User user = new User(
            UserId.of(UUID.randomUUID()),
            "test@example.com",
            hashedPassword,
            "Test User",
            Role.USER,
            true,
            Instant.now(),
            Instant.now()
        );

        LoginUserUseCase.LoginCommand command = 
            new LoginUserUseCase.LoginCommand("test@example.com", rawPassword);

        when(userRepository.findByEmail(command.email())).thenReturn(Optional.of(user));
        when(tokenProvider.generateAccessToken(user)).thenReturn("access-token");
        when(tokenProvider.generateRefreshToken(user)).thenReturn("refresh-token");
        when(tokenProvider.getJwtExpiration()).thenReturn(3600000L);

        LoginUserUseCase.LoginResult result = authenticationService.login(command);

        assertNotNull(result);
        assertEquals("access-token", result.accessToken());
        assertEquals("refresh-token", result.refreshToken());
        assertEquals(user, result.user());
        verify(tokenProvider).generateAccessToken(user);
        verify(tokenProvider).generateRefreshToken(user);
    }

    @Test
    void shouldNotLoginWithInvalidEmail() {
        LoginUserUseCase.LoginCommand command = 
            new LoginUserUseCase.LoginCommand("nonexistent@example.com", "password123");

        when(userRepository.findByEmail(command.email())).thenReturn(Optional.empty());

        assertThrows(BadCredentialsException.class, () -> authenticationService.login(command));
    }

    @Test
    void shouldNotLoginWithInvalidPassword() {
        String hashedPassword = passwordEncoder.encode("correctPassword");
        
        User user = new User(
            UserId.of(UUID.randomUUID()),
            "test@example.com",
            hashedPassword,
            "Test User",
            Role.USER,
            true,
            Instant.now(),
            Instant.now()
        );

        LoginUserUseCase.LoginCommand command = 
            new LoginUserUseCase.LoginCommand("test@example.com", "wrongPassword");

        when(userRepository.findByEmail(command.email())).thenReturn(Optional.of(user));

        assertThrows(BadCredentialsException.class, () -> authenticationService.login(command));
    }

    @Test
    void shouldNotLoginDisabledUser() {
        String rawPassword = "password123";
        String hashedPassword = passwordEncoder.encode(rawPassword);
        
        User user = new User(
            UserId.of(UUID.randomUUID()),
            "test@example.com",
            hashedPassword,
            "Test User",
            Role.USER,
            false, // disabled
            Instant.now(),
            Instant.now()
        );

        LoginUserUseCase.LoginCommand command = 
            new LoginUserUseCase.LoginCommand("test@example.com", rawPassword);

        when(userRepository.findByEmail(command.email())).thenReturn(Optional.of(user));

        assertThrows(BadCredentialsException.class, () -> authenticationService.login(command));
    }

    @Test
    void shouldRefreshTokens() {
        String refreshToken = "valid-refresh-token";
        
        User user = new User(
            UserId.of(UUID.randomUUID()),
            "test@example.com",
            "hashedPassword",
            "Test User",
            Role.USER,
            true,
            Instant.now(),
            Instant.now()
        );

        when(tokenProvider.validateToken(refreshToken)).thenReturn(true);
        when(tokenProvider.isRefreshToken(refreshToken)).thenReturn(true);
        when(tokenProvider.getEmailFromToken(refreshToken)).thenReturn(user.email());
        when(userRepository.findByEmail(user.email())).thenReturn(Optional.of(user));
        when(tokenProvider.generateAccessToken(user)).thenReturn("new-access-token");
        when(tokenProvider.generateRefreshToken(user)).thenReturn("new-refresh-token");
        when(tokenProvider.getJwtExpiration()).thenReturn(3600000L);

        RefreshTokenUseCase.AuthTokens result = authenticationService.refresh(refreshToken);

        assertNotNull(result);
        assertEquals("new-access-token", result.accessToken());
        assertEquals("new-refresh-token", result.refreshToken());
    }

    @Test
    void shouldNotRefreshInvalidToken() {
        String invalidToken = "invalid-token";

        when(tokenProvider.validateToken(invalidToken)).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authenticationService.refresh(invalidToken));
    }

    @Test
    void shouldNotRefreshAccessTokenAsRefreshToken() {
        String accessToken = "access-token";

        when(tokenProvider.validateToken(accessToken)).thenReturn(true);
        when(tokenProvider.isRefreshToken(accessToken)).thenReturn(false);

        assertThrows(BadCredentialsException.class, () -> authenticationService.refresh(accessToken));
    }
}
