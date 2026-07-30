package org.example.nabat.identity.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;

/**
 * A single-use email verification or password-reset token.
 *
 * <h2>Only the hash is stored</h2>
 * {@link #id()} is the SHA-256 of the secret, not the secret itself. The raw value
 * exists only in {@link Issued#rawValue()}, long enough to be put in an email, and
 * is never persisted.
 *
 * <p>Previously the raw token <em>was</em> the primary key, in plaintext. Anyone
 * with read access to the {@code verification_tokens} table — a database backup, a
 * read replica, an SQL-injection foothold, a support engineer — could take over any
 * account by using an outstanding password-reset token. Hashing means a stolen table
 * yields nothing usable, exactly as for passwords.
 */
public record VerificationToken(
    /** SHA-256 hash (Base64url) of the secret that was emailed. Never the secret itself. */
    String id,
    UserId userId,
    VerificationTokenType type,
    Instant expiresAt,
    boolean used,
    Instant createdAt
) {
    private static final int EMAIL_VERIFICATION_EXPIRY_HOURS = 24;
    private static final int PASSWORD_RESET_EXPIRY_HOURS = 1;

    /** 256 bits of entropy, matching the digest width. */
    private static final int TOKEN_BYTES = 32;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * A freshly created token: the record to persist, plus the secret to email.
     *
     * @param rawValue the secret — send it to the user, never write it down
     */
    public record Issued(VerificationToken token, String rawValue) {
    }

    /** Factory: create an email-verification token (valid 24 h). */
    public static Issued createEmailVerification(UserId userId) {
        return create(userId, VerificationTokenType.EMAIL_VERIFICATION, EMAIL_VERIFICATION_EXPIRY_HOURS);
    }

    /** Factory: create a password-reset token (valid 1 h). */
    public static Issued createPasswordReset(UserId userId) {
        return create(userId, VerificationTokenType.PASSWORD_RESET, PASSWORD_RESET_EXPIRY_HOURS);
    }

    private static Issued create(UserId userId, VerificationTokenType type, int expiryHours) {
        Instant now = Instant.now();
        String rawValue = generateSecret();
        VerificationToken token = new VerificationToken(
            hash(rawValue),
            userId,
            type,
            now.plus(expiryHours, ChronoUnit.HOURS),
            false,
            now
        );
        return new Issued(token, rawValue);
    }

    /**
     * Hashes a presented token so it can be looked up against stored hashes.
     *
     * <p>A plain digest rather than a password hash is appropriate here: the input is
     * 256 bits of {@link SecureRandom} output, so there is no dictionary to attack
     * and no need for a work factor.
     */
    public static String hash(String rawValue) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(rawValue.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is mandated by the JLS; unreachable on any conformant JVM.
            throw new IllegalStateException("SHA-256 is unavailable", e);
        }
    }

    private static String generateSecret() {
        byte[] bytes = new byte[TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Returns {@code true} when the token is past its expiry instant. */
    public boolean isExpired() {
        return Instant.now().isAfter(expiresAt);
    }

    /** Returns an immutable copy of this record with {@code used = true}. */
    public VerificationToken markUsed() {
        return new VerificationToken(id, userId, type, expiresAt, true, createdAt);
    }
}
