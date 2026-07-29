package org.example.nabat.application.port.out;

import org.example.nabat.domain.model.User;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Mints and verifies the tokens that carry a session.
 *
 * <p>The parse methods return the decoded claims in one call. The previous shape —
 * {@code validateToken} / {@code isRefreshToken} / {@code getEmailFromToken} as
 * separate booleans and getters — meant the authentication filter verified the same
 * signature three times on every single request, and left it to each caller to
 * remember to check the token type before trusting the claims.
 */
public interface TokenProvider {

    String generateAccessToken(User user);

    String generateRefreshToken(User user);

    /**
     * Verifies the signature and that this is an <em>access</em> token.
     *
     * @return the claims, or empty if the token is invalid, expired, or of the
     *         wrong type
     */
    Optional<AccessTokenClaims> parseAccessToken(String token);

    /** Verifies the signature and that this is a <em>refresh</em> token. */
    Optional<RefreshTokenClaims> parseRefreshToken(String token);

    /** Access-token lifetime in milliseconds, as reported to clients. */
    long getJwtExpiration();

    /** How long a refresh token remains valid — also the reuse-detection window. */
    Duration refreshTokenLifetime();

    record AccessTokenClaims(UUID userId, String email, String role, int tokenVersion) {
    }

    /**
     * @param tokenId the {@code jti}, used to detect replay of an already-exchanged
     *                refresh token
     */
    record RefreshTokenClaims(UUID userId, String tokenId, int tokenVersion) {
    }
}
