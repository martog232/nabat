package org.example.nabat.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import org.example.nabat.domain.model.*;

import java.time.Instant;
import java.util.UUID;

/**
 * JPA mapping for {@code alerts}.
 *
 * <p>No {@code @Setter}: a blanket setter on every field let callers mutate an alert's
 * {@code status} directly, bypassing {@link Alert#resolve()} — the state transition
 * AGENTS.md names as the only supported one. State changes go through the domain
 * record and then {@link #from(Alert)}.
 */
@Entity
@Getter
@Table(name = "alerts")
public class AlertJpaEntity {

    @Id
    private UUID id;

    /**
     * Optimistic locking. {@code save()} writes every column, so two concurrent
     * writers — say a resolve and a vote-count sync — would otherwise silently
     * overwrite each other's changes. With a version column the loser gets an
     * {@code OptimisticLockException} instead.
     */
    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertSeverity severity;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status;

    @Column(nullable = false)
    private UUID reportedBy;

    @Column(name = "upvote_count", nullable = false)
    private int upvoteCount = 0;

    @Column(name = "downvote_count", nullable = false)
    private int downvoteCount = 0;

    @Column(name = "confirmation_count", nullable = false)
    private int confirmationCount = 0;

    @Column(name = "credibility_score", nullable = false)
    private int credibilityScore = 0;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "photo_url", length = 500)
    private String photoUrl;

    // JPA requires default constructor
    protected AlertJpaEntity() {
    }

    public static AlertJpaEntity from(Alert alert) {
        AlertJpaEntity entity = new AlertJpaEntity();
        entity.id = alert.id().value();
        entity.title = alert.title();
        entity.description = alert.description();
        entity.type = alert.type();
        entity.severity = alert.severity();
        entity.latitude = alert.location().latitude();
        entity.longitude = alert.location().longitude();
        entity.createdAt = alert.createdAt();
        entity.status = alert.status();
        entity.reportedBy = alert.reportedBy();
        entity.upvoteCount = alert.upvoteCount();
        entity.downvoteCount = alert.downvoteCount();
        entity.confirmationCount = alert.confirmationCount();
        // Carried through, never recomputed — the voting service owns this value.
        entity.credibilityScore = alert.credibilityScore();
        entity.resolvedAt = alert.resolvedAt();
        entity.photoUrl = alert.photoUrl();
        return entity;
    }

    public Alert toDomain() {
        return new Alert(
                AlertId.of(id),
                title,
                description,
                type,
                severity,
                Location.of(latitude, longitude),
                createdAt,
                status,
                reportedBy,
                upvoteCount,
                downvoteCount,
                confirmationCount,
                credibilityScore,
                resolvedAt,
                photoUrl
        );
    }
}
