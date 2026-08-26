package org.example.nabat.identity.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.identity.domain.VerificationToken;
import org.example.nabat.identity.domain.VerificationTokenType;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "verification_tokens")
public class VerificationTokenJpaEntity {

    /**
     * The SHA-256 hash of the emailed secret, Base64url without padding — 43 characters, not
     * a UUID. This declared 36 and the column matched it (V3), so every insert was rejected
     * and no token was ever stored. Kept in step with the widened column in V13; with
     * {@code ddl-auto=validate}, a length that disagrees with the schema fails at startup
     * rather than at the first write.
     */
    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VerificationTokenType type;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean used;

    @Column(nullable = false)
    private Instant createdAt;

    protected VerificationTokenJpaEntity() {
    }

    public static VerificationTokenJpaEntity from(VerificationToken token) {
        VerificationTokenJpaEntity e = new VerificationTokenJpaEntity();
        e.id = token.id();
        e.userId = token.userId().value();
        e.type = token.type();
        e.expiresAt = token.expiresAt();
        e.used = token.used();
        e.createdAt = token.createdAt();
        return e;
    }

    public VerificationToken toDomain() {
        return new VerificationToken(id, UserId.of(userId), type, expiresAt, used, createdAt);
    }
}

