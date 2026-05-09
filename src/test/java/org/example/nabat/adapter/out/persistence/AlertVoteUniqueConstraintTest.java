package org.example.nabat.adapter.out.persistence;

import org.example.nabat.domain.model.VoteType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Confirms that the {@code uk_alert_votes_alert_user} unique constraint on
 * {@code alert_votes(alert_id, user_id)} is enforced at the database level.
 *
 * <p>This is the persistence-layer guard that prevents a race condition where
 * two concurrent requests from the same user vote on the same alert before the
 * application-level idempotency check (in {@code AlertVoteService}) has a
 * chance to run.
 */
@DataJpaTest
class AlertVoteUniqueConstraintTest {

    @Autowired
    private AlertVoteJpaRepository repository;

    @Test
    void savingDuplicateVoteForSameAlertAndUserViolatesUniqueConstraint() {
        UUID alertId = UUID.randomUUID();
        UUID userId  = UUID.randomUUID();

        AlertVoteJpaEntity first = entity(alertId, userId, VoteType.UPVOTE);
        repository.saveAndFlush(first);

        // A second entity with the same (alertId, userId) pair — different id and voteType —
        // must be rejected by the uk_alert_votes_alert_user constraint.
        AlertVoteJpaEntity duplicate = entity(alertId, userId, VoteType.DOWNVOTE);

        assertThatThrownBy(() -> repository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameUserCanVoteOnDifferentAlerts() {
        UUID userId   = UUID.randomUUID();
        UUID alertId1 = UUID.randomUUID();
        UUID alertId2 = UUID.randomUUID();

        repository.saveAndFlush(entity(alertId1, userId, VoteType.UPVOTE));
        // Must not throw — different alert.
        repository.saveAndFlush(entity(alertId2, userId, VoteType.UPVOTE));
    }

    @Test
    void differentUsersCanVoteOnSameAlert() {
        UUID alertId = UUID.randomUUID();
        UUID userId1 = UUID.randomUUID();
        UUID userId2 = UUID.randomUUID();

        repository.saveAndFlush(entity(alertId, userId1, VoteType.UPVOTE));
        // Must not throw — different user.
        repository.saveAndFlush(entity(alertId, userId2, VoteType.CONFIRM));
    }

    // -------------------------------------------------------------------------

    private static AlertVoteJpaEntity entity(UUID alertId, UUID userId, VoteType type) {
        AlertVoteJpaEntity e = new AlertVoteJpaEntity();
        e.setId(UUID.randomUUID());
        e.setAlertId(alertId);
        e.setUserId(userId);
        e.setVoteType(type);
        e.setCreatedAt(Instant.now());
        return e;
    }
}

