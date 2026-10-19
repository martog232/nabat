package org.example.nabat.incident.adapter.out.persistence;

import org.example.nabat.PostgresTestSupport;
import org.example.nabat.identity.adapter.out.persistence.UserJpaEntity;
import org.example.nabat.identity.adapter.out.persistence.UserJpaRepository;
import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.ClosedBy;
import org.example.nabat.incident.domain.FixCheckTally;
import org.example.nabat.incident.domain.FixVerdict;
import org.example.nabat.shared.domain.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import jakarta.persistence.EntityManager;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The answers and the tally on the report, against the real V24 schema.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JdbcFixCheckStore.class)
class JdbcFixCheckStoreIntegrationTest extends PostgresTestSupport {

    @Autowired private JdbcFixCheckStore store;
    @Autowired private AlertJpaRepository alertJpa;
    @Autowired private UserJpaRepository users;
    @Autowired private EntityManager entityManager;

    private UUID reporterId;
    private AlertId alertId;

    @BeforeEach
    void setUp() {
        alertJpa.deleteAllInBatch();
        User reporter = User.create("fix-check-" + UUID.randomUUID() + "@example.com", "hash", "Подател");
        users.saveAndFlush(UserJpaEntity.from(reporter));
        reporterId = reporter.id().value();
        alertId = AlertId.generate();
        alertJpa.saveAndFlush(AlertJpaEntity.from(new Alert(alertId, "Дупка", "Дълбока", 1007,
            Location.of(42.67, 23.32), Instant.now(), AlertStatus.RESOLVED, reporterId,
            0, 0, 0, 0, Instant.now(), null, "LOZENETS", ClosedBy.AUTHORITY)));
    }

    @Test
    void countsEachPersonOnceAndKeepsTheReportersAnswerApart() {
        store.record(alertId.value(), UUID.randomUUID(), FixVerdict.FIXED, false, Instant.now());
        store.record(alertId.value(), UUID.randomUUID(), FixVerdict.PARTLY, false, Instant.now());
        FixCheckTally tally = store.record(alertId.value(), reporterId, FixVerdict.NOT_FIXED, true, Instant.now());

        assertThat(tally).isEqualTo(new FixCheckTally(1, 1, 1, FixVerdict.NOT_FIXED));
    }

    @Test
    void aChangedMindReplacesTheEarlierAnswer() {
        UUID someone = UUID.randomUUID();
        store.record(alertId.value(), someone, FixVerdict.NOT_FIXED, false, Instant.now());

        FixCheckTally tally = store.record(alertId.value(), someone, FixVerdict.FIXED, false, Instant.now());

        assertThat(tally).isEqualTo(new FixCheckTally(1, 0, 0, null));
        assertThat(store.verdictOf(alertId.value(), someone)).contains(FixVerdict.FIXED);
    }

    @Test
    void withdrawingTakesTheAnswerOutOfTheCount() {
        store.record(alertId.value(), reporterId, FixVerdict.NOT_FIXED, true, Instant.now());

        FixCheckTally tally = store.withdraw(alertId.value(), reporterId);

        assertThat(tally).isEqualTo(FixCheckTally.NONE);
        assertThat(store.verdictOf(alertId.value(), reporterId)).isEmpty();
    }

    /** The report read back carries the tally the store wrote. */
    @Test
    void theReportCarriesItsTally() {
        store.record(alertId.value(), UUID.randomUUID(), FixVerdict.FIXED, false, Instant.now());
        entityManager.clear();

        assertThat(reload().fixCheck()).isEqualTo(new FixCheckTally(1, 0, 0, null));
    }

    /**
     * The columns are read-only on the entity, so a report loaded before an answer and
     * saved after it cannot write its stale counts back.
     */
    @Test
    void savingAReportDoesNotOverwriteItsTally() {
        AlertJpaEntity loadedBefore = alertJpa.findById(alertId.value()).orElseThrow();
        store.record(alertId.value(), UUID.randomUUID(), FixVerdict.NOT_FIXED, false, Instant.now());

        // What AlertRepositoryAdapter.save does with an existing report.
        loadedBefore.copyStateFrom(loadedBefore.toDomain().withVoteCounts(3, 0, 0, 10));
        entityManager.flush();
        entityManager.clear();

        assertThat(reload().fixCheck()).isEqualTo(new FixCheckTally(0, 0, 1, null));
    }

    private Alert reload() {
        return alertJpa.findById(alertId.value()).orElseThrow().toDomain();
    }
}
