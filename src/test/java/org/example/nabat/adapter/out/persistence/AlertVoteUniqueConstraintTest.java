package org.example.nabat.adapter.out.persistence;

import org.example.nabat.PostgresTestSupport;
import org.example.nabat.domain.model.AlertSeverity;
import org.example.nabat.domain.model.AlertStatus;
import org.example.nabat.domain.model.AlertType;
import org.example.nabat.domain.model.Role;
import org.example.nabat.voting.adapter.out.persistence.AlertVoteJpaEntity;
import org.example.nabat.voting.adapter.out.persistence.AlertVoteJpaRepository;
import org.example.nabat.voting.domain.model.VoteType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Confirms that the {@code uk_alert_votes_alert_user} unique constraint on
 * {@code alert_votes(alert_id, user_id)} is enforced at the database level.
 *
 * <p>Parent records (users + alerts) are seeded in {@code @BeforeEach} to satisfy
 * the FK constraints that PostgreSQL enforces strictly (unlike H2 in permissive mode).
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AlertVoteUniqueConstraintTest extends PostgresTestSupport {

    @Autowired private AlertVoteJpaRepository repository;
    @Autowired private AlertJpaRepository alertRepository;
    @Autowired private UserJpaRepository userRepository;

    // IDs seeded per test (each test runs in its own rolled-back transaction).
    private UUID userId1;
    private UUID userId2;
    private UUID alertId1;
    private UUID alertId2;

    @BeforeEach
    void seedParents() {
        userId1  = saveUser("u1-constraint@test.com");
        userId2  = saveUser("u2-constraint@test.com");
        alertId1 = saveAlert(userId1, "Alert A");
        alertId2 = saveAlert(userId1, "Alert B");
    }

    @Test
    void savingDuplicateVoteForSameAlertAndUserViolatesUniqueConstraint() {
        repository.saveAndFlush(entity(alertId1, userId1, VoteType.UPVOTE));

        // A second entity with the same (alertId, userId) pair — different id and voteType —
        // must be rejected by the uk_alert_votes_alert_user constraint.
        assertThatThrownBy(() -> repository.saveAndFlush(entity(alertId1, userId1, VoteType.DOWNVOTE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sameUserCanVoteOnDifferentAlerts() {
        repository.saveAndFlush(entity(alertId1, userId1, VoteType.UPVOTE));
        // Must not throw — different alert.
        repository.saveAndFlush(entity(alertId2, userId1, VoteType.UPVOTE));
    }

    @Test
    void differentUsersCanVoteOnSameAlert() {
        repository.saveAndFlush(entity(alertId1, userId1, VoteType.UPVOTE));
        // Must not throw — different user.
        repository.saveAndFlush(entity(alertId1, userId2, VoteType.CONFIRM));
    }

    // -------------------------------------------------------------------------

    private UUID saveUser(String email) {
        UserJpaEntity u = new UserJpaEntity();
        u.setId(UUID.randomUUID());
        u.setEmail(email);
        u.setPassword("hash");
        u.setDisplayName("User");
        u.setRole(Role.USER);
        u.setEnabled(true);
        u.setEmailVerified(true);
        u.setCreatedAt(Instant.now());
        u.setUpdatedAt(Instant.now());
        u.setNotificationRadiusKm(5);
        return userRepository.saveAndFlush(u).getId();
    }

    private UUID saveAlert(UUID reportedBy, String title) {
        AlertJpaEntity a = new AlertJpaEntity();
        a.setId(UUID.randomUUID());
        a.setTitle(title);
        a.setDescription("desc");
        a.setType(AlertType.FIRE);
        a.setSeverity(AlertSeverity.MEDIUM);
        a.setLatitude(42.6977);
        a.setLongitude(23.3219);
        a.setCreatedAt(Instant.now());
        a.setStatus(AlertStatus.ACTIVE);
        a.setReportedBy(reportedBy);
        return alertRepository.saveAndFlush(a).getId();
    }

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
