package org.example.nabat.domain.model;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

public record VerificationToken(
    String id,         // UUID string — acts as the secret token sent via email
    UserId userId,
    VerificationTokenType type,
    Instant expiresAt,
    boolean used,
    Instant createdAt
) {
    private static final int EMAIL_VERIFICATION_EXPIRY_HOURS = 24;
    private static final int PASSWORD_RESET_EXPIRY_HOURS = 1;

    /** Factory: create an email-verification token (valid 24 h). */
    public static VerificationToken createEmailVerification(UserId userId) {
        Instant now = Instant.now();
        return new VerificationToken(
            UUID.randomUUID().toString(),
            userId,
            VerificationTokenType.EMAIL_VERIFICATION,
            now.plus(EMAIL_VERIFICATION_EXPIRY_HOURS, ChronoUnit.HOURS),
            false,
            now
        );
    }

    /** Factory: create a password-reset token (valid 1 h). */
    public static VerificationToken createPasswordReset(UserId userId) {
        Instant now = Instant.now();
        return new VerificationToken(
            UUID.randomUUID().toString(),
            userId,
            VerificationTokenType.PASSWORD_RESET,
            now.plus(PASSWORD_RESET_EXPIRY_HOURS, ChronoUnit.HOURS),
            false,
            now
        );
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

