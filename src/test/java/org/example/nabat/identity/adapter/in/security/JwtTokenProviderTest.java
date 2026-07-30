package org.example.nabat.identity.adapter.in.security;

import org.example.nabat.identity.application.port.out.TokenProvider;
import org.example.nabat.identity.domain.User;
import org.example.nabat.testsupport.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JwtTokenProviderTest {

    private static final String VALID_SECRET =
        "Xk92LmQp7ZrT4vBn1YsWc6HjE3aFgU8oPd5RtNqM0iKlZbCyXwVuAsDfGh";

    private JwtTokenProvider jwtTokenProvider;
    private User testUser;

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider(VALID_SECRET, 3600000L, 86400000L);
        testUser = Fixtures.user();
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
    void accessTokenCarriesIdentityAndTokenVersion() {
        User user = testUser.toBuilder().tokenVersion(4).build();

        Optional<TokenProvider.AccessTokenClaims> claims =
            jwtTokenProvider.parseAccessToken(jwtTokenProvider.generateAccessToken(user));

        assertTrue(claims.isPresent());
        assertEquals(user.id().value(), claims.get().userId());
        assertEquals(user.email(), claims.get().email());
        assertEquals(user.role().name(), claims.get().role());
        assertEquals(4, claims.get().tokenVersion());
    }

    @Test
    void shouldNotParseGarbageAsAccessToken() {
        assertTrue(jwtTokenProvider.parseAccessToken("invalid.token.here").isEmpty());
    }

    /** A token signed with a different key must not be accepted. */
    @Test
    void shouldNotParseTokenSignedWithAnotherSecret() {
        JwtTokenProvider other = new JwtTokenProvider(
            "Qw83MnBv6XtR2sYh9UjKl4PdZaFgCe7oNi1TrMqW5bVxSyDfGhJkLp", 3600000L, 86400000L);

        String foreignToken = other.generateAccessToken(testUser);

        assertTrue(jwtTokenProvider.parseAccessToken(foreignToken).isEmpty());
    }

    @Test
    void refreshTokenCarriesAUniqueTokenId() {
        Optional<TokenProvider.RefreshTokenClaims> first =
            jwtTokenProvider.parseRefreshToken(jwtTokenProvider.generateRefreshToken(testUser));
        Optional<TokenProvider.RefreshTokenClaims> second =
            jwtTokenProvider.parseRefreshToken(jwtTokenProvider.generateRefreshToken(testUser));

        assertTrue(first.isPresent());
        assertTrue(second.isPresent());
        assertNotNull(first.get().tokenId());
        // A distinct jti per token is what makes single-use rotation possible.
        assertFalse(first.get().tokenId().equals(second.get().tokenId()));
    }

    /** The two token types must not be interchangeable. */
    @Test
    void accessAndRefreshTokensAreNotInterchangeable() {
        String accessToken = jwtTokenProvider.generateAccessToken(testUser);
        String refreshToken = jwtTokenProvider.generateRefreshToken(testUser);

        assertTrue(jwtTokenProvider.parseAccessToken(accessToken).isPresent());
        assertTrue(jwtTokenProvider.parseRefreshToken(refreshToken).isPresent());

        assertTrue(jwtTokenProvider.parseAccessToken(refreshToken).isEmpty());
        assertTrue(jwtTokenProvider.parseRefreshToken(accessToken).isEmpty());
    }

    @Test
    void shouldReturnJwtExpiration() {
        assertEquals(3600000L, jwtTokenProvider.getJwtExpiration());
    }

    @Test
    void shouldRejectShortSecret() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> new JwtTokenProvider("too-short-secret", 1000L, 2000L));
        assertTrue(ex.getMessage().contains("too short"));
    }

    @Test
    void shouldRejectBlankSecret() {
        assertThrows(IllegalStateException.class,
            () -> new JwtTokenProvider("   ", 1000L, 2000L));
        assertThrows(IllegalStateException.class,
            () -> new JwtTokenProvider(null, 1000L, 2000L));
    }

    @Test
    void shouldRejectSecretWithLowEntropy() {
        // Long enough, but only two distinct characters.
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> new JwtTokenProvider("ababababababababababababababababababab", 1000L, 2000L));
        assertTrue(ex.getMessage().contains("entropy"));
    }

    /**
     * Every placeholder that has actually been committed to this repository must be
     * refused. The original guard only looked for "change-me-before-production", which
     * none of the real defaults contained — so it never fired.
     */
    @Test
    void shouldRejectEveryPreviouslyCommittedPlaceholder() {
        String[] committedPlaceholders = {
            "nabat-local-dev-jwt-secret-key-min-256-bits-for-local-development-only-123456",
            "nabat-local-docker-jwt-secret-key-min-256-bits-for-development-only-123456",
            "change-me-before-production-use-a-real-secret-at-least-32-chars"
        };

        for (String placeholder : committedPlaceholders) {
            IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> new JwtTokenProvider(placeholder, 1000L, 2000L),
                "should have refused: " + placeholder);
            assertTrue(ex.getMessage().contains("placeholder"),
                "expected a placeholder complaint for: " + placeholder);
        }
    }
}
