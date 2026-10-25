package org.example.nabat.incident.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.ClosedBy;
import org.example.nabat.incident.domain.FixCheckTally;
import org.example.nabat.incident.domain.FixVerdict;
import org.example.nabat.shared.domain.Location;

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

    @Column(name = "category_code", nullable = false)
    private int categoryCode;

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

    @Column(name = "district_code", length = 32)
    private String districtCode;

    @Column(name = "authority_code", length = 32)
    private String authorityCode;

    /** When the authority has to have answered by (V32); null when nothing is waiting on it. */
    @Column(name = "answer_due_at")
    private Instant answerDueAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "closed_by", length = 20)
    private ClosedBy closedBy;

    /*
     * The fix-check tally (V24). Read-only here: JdbcFixCheckStore recounts these from
     * fix_checks under a lock on this row, and a save of a report loaded before a check
     * must not write the counts it loaded back over the new ones.
     */
    @Column(name = "fix_fixed_count", insertable = false, updatable = false)
    private int fixFixedCount;

    @Column(name = "fix_partly_count", insertable = false, updatable = false)
    private int fixPartlyCount;

    @Column(name = "fix_not_fixed_count", insertable = false, updatable = false)
    private int fixNotFixedCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "reporter_fix_verdict", insertable = false, updatable = false)
    private FixVerdict reporterFixVerdict;

    // JPA requires default constructor
    protected AlertJpaEntity() {
    }

    /**
     * A new row. Only for an alert that has never been saved: the version starts at zero,
     * which is right for an insert and wrong for anything else — see {@link #copyStateFrom}.
     */
    public static AlertJpaEntity from(Alert alert) {
        AlertJpaEntity entity = new AlertJpaEntity();
        entity.id = alert.id().value();
        entity.copyStateFrom(alert);
        return entity;
    }

    /**
     * Writes the domain record's state onto this entity, leaving {@code id} and
     * {@code version} as they are.
     *
     * <p>This is how an existing alert is saved: onto the managed entity loaded in the same
     * transaction, never onto a fresh one. The domain record carries no version, so a fresh
     * entity always claimed version 0 — and the first save of any alert that had already
     * been updated once failed as a concurrent modification. It went unnoticed while an
     * alert was saved at most twice, created and resolved; a report that is also SENT,
     * ACKNOWLEDGED and IN_PROGRESS hit it on the first step. Copying onto the managed entity
     * keeps the version check meaningful: it now covers exactly the window from the load to
     * the commit, which is the one that matters.
     */
    void copyStateFrom(Alert alert) {
        this.title = alert.title();
        this.description = alert.description();
        this.categoryCode = alert.categoryCode();
        this.latitude = alert.location().latitude();
        this.longitude = alert.location().longitude();
        this.createdAt = alert.createdAt();
        this.status = alert.status();
        this.reportedBy = alert.reportedBy();
        this.upvoteCount = alert.upvoteCount();
        this.downvoteCount = alert.downvoteCount();
        this.confirmationCount = alert.confirmationCount();
        // Carried through, never recomputed — the voting service owns this value.
        this.credibilityScore = alert.credibilityScore();
        this.resolvedAt = alert.resolvedAt();
        this.photoUrl = alert.photoUrl();
        this.districtCode = alert.districtCode();
        this.authorityCode = alert.authorityCode();
        this.answerDueAt = alert.answerDueAt();
        this.closedBy = alert.closedBy();
    }

    public Alert toDomain() {
        return new Alert(
                AlertId.of(id),
                title,
                description,
                categoryCode,
                Location.of(latitude, longitude),
                createdAt,
                status,
                reportedBy,
                upvoteCount,
                downvoteCount,
                confirmationCount,
                credibilityScore,
                resolvedAt,
                photoUrl,
                districtCode,
                closedBy,
                new FixCheckTally(fixFixedCount, fixPartlyCount, fixNotFixedCount, reporterFixVerdict),
                authorityCode,
                answerDueAt
        );
    }
}
