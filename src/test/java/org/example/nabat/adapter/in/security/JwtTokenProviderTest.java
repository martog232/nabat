package org.example.nabat.adapter.in.security;

import org.example.nabat.domain.model.Role;
import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;
    private User testUser;

    @BeforeEach
    void setUp() {
        String secret = "test-secret-key-min-256-bits-for-testing-purposes-only-do-not-use-in-production";
        long jwtExpiration = 3600000L; // 1 hour
        long refreshExpiration = 86400000L; // 24 hours

        jwtTokenProvider = new JwtTokenProvider(secret, jwtExpiration, refreshExpiration);
        
        testUser = new User(
            UserId.of(UUID.randomUUID()),
            "test@example.com",
            "hashedPassword",
            "Test User",
            Role.USER,
            true,
            Instant.now(),
            Instant.now()
        );
    }

    @Test
    void shouldGenerateAccessToken() {
        String token = jwtTokenProvider.generateAccessToken(testUser);
        
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void shouldGenerateRefreshToken() {
        String token = jwtTokenProvider.generateRefreshToken(testUser);
        
        assertNotNull(token);
        assertFalse(token.isEmpty());
    }

    @Test
    void shouldExtractEmailFromToken() {
        String token = jwtTokenProvider.generateAccessToken(testUser);
        
        String email = jwtTokenProvider.getEmailFromToken(token);
        
        assertEquals(testUser.email(), email);
    }

    @Test
    void shouldExtractUserIdFromToken() {
        String token = jwtTokenProvider.generateAccessToken(testUser);
        
        String userId = jwtTokenProvider.getUserIdFromToken(token);
        
        assertEquals(testUser.id().value().toString(), userId);
    }

    @Test
    void shouldValidateValidToken() {
        String token = jwtTokenProvider.generateAccessToken(testUser);
        
        boolean isValid = jwtTokenProvider.validateToken(token);
        
        assertTrue(isValid);
    }

    @Test
    void shouldNotValidateInvalidToken() {
        String invalidToken = "invalid.token.here";
        
        boolean isValid = jwtTokenProvider.validateToken(invalidToken);
        
        assertFalse(isValid);
    }

    @Test
    void shouldIdentifyRefreshToken() {
        String refreshToken = jwtTokenProvider.generateRefreshToken(testUser);
        
        boolean isRefresh = jwtTokenProvider.isRefreshToken(refreshToken);
        
        assertTrue(isRefresh);
    }

    @Test
    void shouldNotIdentifyAccessTokenAsRefreshToken() {
        String accessToken = jwtTokenProvider.generateAccessToken(testUser);
        
        boolean isRefresh = jwtTokenProvider.isRefreshToken(accessToken);
        
        assertFalse(isRefresh);
    }

    @Test
    void shouldReturnJwtExpiration() {
        long expiration = jwtTokenProvider.getJwtExpiration();
        
        assertEquals(3600000L, expiration);
    }
}
