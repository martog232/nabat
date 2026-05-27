package org.example.nabat.adapter.out.persistence;

import org.example.nabat.PostgresTestSupport;
import org.example.nabat.domain.model.AlertSeverity;
import org.example.nabat.domain.model.AlertStatus;
import org.example.nabat.domain.model.AlertType;
import org.example.nabat.domain.model.Role;
import org.example.nabat.domain.model.VoteType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AlertVoteJpaRepositoryTest extends PostgresTestSupport {

    @Autowired private AlertVoteJpaRepository voteRepository;
    @Autowired private AlertJpaRepository alertRepository;
    @Autowired private UserJpaRepository userRepository;

    @BeforeEach
    void setUp() {
        voteRepository.deleteAll();
        alertRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void findDeleteExistsAndCountWorkForAlertVotes() {
        UUID userId = saveUser();
        UUID alertId = saveAlert(userId);

        AlertVoteJpaEntity vote = voteEntity(alertId, userId);
        voteRepository.saveAndFlush(vote);

        assertThat(voteRepository.existsByAlertIdAndUserId(alertId, userId)).isTrue();
        assertThat(voteRepository.findByAlertIdAndUserId(alertId, userId))
            .isPresent()
            .get()
            .extracting(AlertVoteJpaEntity::getAlertId, AlertVoteJpaEntity::getUserId, AlertVoteJpaEntity::getVoteType)
            .containsExactly(alertId, userId, VoteType.UPVOTE);
        assertThat(voteRepository.countByAlertIdAndVoteType(alertId, VoteType.UPVOTE)).isEqualTo(1);
        assertThat(voteRepository.countByAlertIdAndVoteType(alertId, VoteType.DOWNVOTE)).isZero();

        voteRepository.deleteByAlertIdAndUserId(alertId, userId);
        voteRepository.flush();

        assertThat(voteRepository.existsByAlertIdAndUserId(alertId, userId)).isFalse();
        assertThat(voteRepository.findByAlertIdAndUserId(alertId, userId)).isEmpty();
    }

    private UUID saveUser() {
        UserJpaEntity user = new UserJpaEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("vote-user@example.com");
        user.setPassword("hash");
        user.setDisplayName("User");
        user.setRole(Role.USER);
        user.setEnabled(true);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return userRepository.saveAndFlush(user).getId();
    }

    private UUID saveAlert(UUID reportedBy) {
        AlertJpaEntity alert = new AlertJpaEntity();
        alert.setId(UUID.randomUUID());
        alert.setTitle("Vote Alert");
        alert.setDescription("desc");
        alert.setType(AlertType.TRAFFIC);
        alert.setSeverity(AlertSeverity.MEDIUM);
        alert.setLatitude(42.6977);
        alert.setLongitude(23.3219);
        alert.setCreatedAt(Instant.now());
        alert.setStatus(AlertStatus.ACTIVE);
        alert.setReportedBy(reportedBy);
        return alertRepository.saveAndFlush(alert).getId();
    }

    private AlertVoteJpaEntity voteEntity(UUID alertId, UUID userId) {
        AlertVoteJpaEntity e = new AlertVoteJpaEntity();
        e.setId(UUID.randomUUID());
        e.setAlertId(alertId);
        e.setUserId(userId);
        e.setVoteType(VoteType.UPVOTE);
        e.setCreatedAt(Instant.now());
        return e;
    }
}
