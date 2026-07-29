package org.example.nabat.adapter.in.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.Getter;
import org.example.nabat.application.port.out.TokenProvider;
import org.example.nabat.domain.model.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Component
public class JwtTokenProvider implements TokenProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenProvider.class);

    private static final int MIN_SECRET_LENGTH = 32;

    /**
     * Minimum number of distinct characters. A long but repetitive string
     * ("aaaa…") passes the length check while carrying almost no entropy.
     */
    private static final int MIN_DISTINCT_CHARS = 16;

    /**
     * Substrings that mark a secret as a development placeholder. Any secret
     * containing one of these is refused outright — these are the strings that
     * have historically been committed to this repository and to its Compose,
     * Helm and Kubernetes manifests, so a deployment that still carries one is
     * signing tokens with a publicly known key.
     *
     * <p>Matched case-insensitively against the whole secret.
     */
    private static final List<String> REJECTED_MARKERS = List.of(
        "change-me",
        "changeme",
        "local-dev",
        "local-docker",
        "development-only",
        "for-development",
        "placeholder",
        "insecure",
        "example",
        "replace-me",
        "dummy",
        "sample"
    );

    public static final String TOKEN_TYPE = "tokenType";
    public static final String REFRESH_TOKEN_TYPE = "refresh";
    public static final String ACCESS_TOKEN_TYPE = "access";

    private static final String CLAIM_USER_ID = "userId";
    private static final String CLAIM_EMAIL = "email";
    private static final String CLAIM_ROLE = "role";
    /** Short name kept deliberately compact — it appears in every token. */
    private static final String CLAIM_TOKEN_VERSION = "tv";

    private final SecretKey secretKey;
    @Getter
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

    /**
     * Fails closed on any secret that is absent, too short, too repetitive, or
     * recognisably a development placeholder.
     *
     * <p>There is deliberately no usable default anywhere in the configuration:
     * an unset {@code JWT_SECRET} must stop startup rather than silently fall
     * back to a value an attacker can read out of version control.
     */
    private static void validateSecret(String secret) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(refusal("jwt.secret is not set"));
        }
        if (secret.length() < MIN_SECRET_LENGTH) {
            throw new IllegalStateException(refusal(
                "jwt.secret is too short (" + secret.length() + " chars, minimum " + MIN_SECRET_LENGTH + ")"));
        }

        String lower = secret.toLowerCase(Locale.ROOT);
        for (String marker : REJECTED_MARKERS) {
            if (lower.contains(marker)) {
                throw new IllegalStateException(refusal(
                    "jwt.secret looks like a development placeholder (contains '" + marker + "')"));
            }
        }

        long distinct = secret.chars().distinct().count();
        if (distinct < MIN_DISTINCT_CHARS) {
            throw new IllegalStateException(refusal(
                "jwt.secret has too little entropy (" + distinct + " distinct characters, minimum "
                    + MIN_DISTINCT_CHARS + ")"));
        }
    }

    private static String refusal(String problem) {
        return problem + ". Refusing to start.\n"
            + "Set the JWT_SECRET environment variable to a strong random value, e.g.\n"
            + "    openssl rand -base64 48\n"
            + "Both nabat-app and nabat-voting must be given the SAME secret, otherwise "
            + "tokens issued here will not validate there.";
    }

    @Override
    public String generateAccessToken(User user) {
        return Jwts.builder()
            .subject(user.email())
            .claim(CLAIM_USER_ID, user.id().value().toString())
            .claim(CLAIM_EMAIL, user.email())
            .claim(CLAIM_ROLE, user.role().name())
            .claim(CLAIM_TOKEN_VERSION, user.tokenVersion())
            .claim(TOKEN_TYPE, ACCESS_TOKEN_TYPE)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
            .signWith(secretKey)
            .compact();
    }

    /**
     * Refresh tokens carry a {@code jti} so that each can be marked as consumed on
     * exchange, giving single-use rotation with replay detection.
     */
    @Override
    public String generateRefreshToken(User user) {
        return Jwts.builder()
            .id(UUID.randomUUID().toString())
            .subject(user.email())
            .claim(CLAIM_USER_ID, user.id().value().toString())
            .claim(CLAIM_TOKEN_VERSION, user.tokenVersion())
            .claim(TOKEN_TYPE, REFRESH_TOKEN_TYPE)
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + refreshExpiration))
            .signWith(secretKey)
            .compact();
    }

    @Override
    public Optional<AccessTokenClaims> parseAccessToken(String token) {
        return parse(token, ACCESS_TOKEN_TYPE).flatMap(claims -> {
            UUID userId = readUserId(claims);
            if (userId == null) {
                return Optional.empty();
            }
            return Optional.of(new AccessTokenClaims(
                userId,
                claims.get(CLAIM_EMAIL, String.class),
                claims.get(CLAIM_ROLE, String.class),
                readTokenVersion(claims)
            ));
        });
    }

    @Override
    public Optional<RefreshTokenClaims> parseRefreshToken(String token) {
        return parse(token, REFRESH_TOKEN_TYPE).flatMap(claims -> {
            UUID userId = readUserId(claims);
            String tokenId = claims.getId();
            if (userId == null || tokenId == null || tokenId.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new RefreshTokenClaims(userId, tokenId, readTokenVersion(claims)));
        });
    }

    @Override
    public Duration refreshTokenLifetime() {
        return Duration.ofMillis(refreshExpiration);
    }

    /** Single signature verification, plus the token-type check callers used to forget. */
    private Optional<Claims> parse(String token, String requiredType) {
        try {
            Claims claims = Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            if (!requiredType.equals(claims.get(TOKEN_TYPE, String.class))) {
                log.debug("Rejected token: expected type {}", requiredType);
                return Optional.empty();
            }
            return Optional.of(claims);
        } catch (JwtException | IllegalArgumentException e) {
            // Debug, not error: malformed tokens are attacker-controlled input, and
            // logging a stack trace per attempt is a log-flooding lever.
            log.debug("Rejected token: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private static UUID readUserId(Claims claims) {
        String raw = claims.get(CLAIM_USER_ID, String.class);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Tokens minted before the claim existed are treated as version 0. */
    private static int readTokenVersion(Claims claims) {
        Integer version = claims.get(CLAIM_TOKEN_VERSION, Integer.class);
        return version == null ? 0 : version;
    }
}
