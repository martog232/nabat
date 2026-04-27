package org.example.nabat.adapter.in.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.example.nabat.application.port.out.TokenProvider;
import org.example.nabat.domain.model.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtTokenProvider implements TokenProvider {

    private static final int MIN_SECRET_LENGTH = 32;
    /** Substring that flags the documented dev placeholder in application.properties. */
    private static final String PLACEHOLDER_MARKER = "change-me-before-production";

    private final SecretKey secretKey;
    private final long jwtExpiration;
    private final long refreshExpiration;

    public JwtTokenProvider(
        @Value("${jwt.secret}") String secret,
        @Value("${jwt.expiration}") long jwtExpiration,
        @Value("${jwt.refresh-expiration}") long refreshExpiration
    ) {
        validateSecret(secret);
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.jwtExpiration = jwtExpiration;
        this.refreshExpiration = refreshExpiration;
    }

    private static void validateSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                "jwt.secret is not set. Refusing to start. " +
                "Provide JWT_SECRET environment variable with a strong secret (>= " + MIN_SECRET_LENGTH + " chars)."
            );
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(
                "jwt.secret is too short (" + secret.length() + " chars). " +
                "Refusing to start. Minimum length is " + MIN_SECRET_LENGTH + " characters."
            );
        }
        if (secret.contains(PLACEHOLDER_MARKER)) {
            throw new IllegalStateException(
                "jwt.secret still contains the dev placeholder marker '" + PLACEHOLDER_MARKER + "'. " +
                "Refusing to start. Set JWT_SECRET to a real secret before deploying."
            );
        }
    }

    public String generateAccessToken(User user) {
        return generateToken(user, jwtExpiration, "access");
    }

    public String generateRefreshToken(User user) {
        return generateToken(user, refreshExpiration, "refresh");
    }

    private String generateToken(User user, long expiration, String tokenType) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.id().value().toString());
        claims.put("email", user.email());
        claims.put("role", user.role().name());
        claims.put("tokenType", tokenType);

        return Jwts.builder()
            .claims(claims)
            .subject(user.email())
            .issuedAt(now)
            .expiration(expiryDate)
            .signWith(secretKey)
            .compact();
    }

    public String getEmailFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.getSubject();
    }

    public String getUserIdFromToken(String token) {
        Claims claims = parseToken(token);
        return claims.get("userId", String.class);
    }

    public boolean validateToken(String token) {
        try {
            parseToken(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isRefreshToken(String token) {
        try {
            Claims claims = parseToken(token);
            String tokenType = claims.get("tokenType", String.class);
            return "refresh".equals(tokenType);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    public boolean isAccessToken(String token) {
        try {
            Claims claims = parseToken(token);
            String tokenType = claims.get("tokenType", String.class);
            return "access".equals(tokenType);
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    private Claims parseToken(String token) {
        return Jwts.parser()
            .verifyWith(secretKey)
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    public long getJwtExpiration() {
        return jwtExpiration;
    }
}
