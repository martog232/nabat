package org.example.nabat.incident.adapter.out.persistence;

import org.example.nabat.PostgresTestSupport;
import org.example.nabat.identity.adapter.out.persistence.UserJpaEntity;
import org.example.nabat.identity.adapter.out.persistence.UserJpaRepository;
import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertSeverity;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.AlertType;
import org.example.nabat.shared.domain.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the query behind the orphaned-photo sweep.
 *
 * <p>Worth an integration test rather than a mock, for two reasons. It is a native query
 * using {@code unnest(CAST(:filenames AS text[]))}, so whether Hibernate binds a Java
 * {@code String[]} as a PostgreSQL text array is a fact about the driver, not something a
 * unit test can assert. And the result decides what gets deleted — a query that silently
 * returns nothing would make the sweeper erase every photo it looked at.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AlertPhotoReferenceQueryIntegrationTest extends PostgresTestSupport {

    @Autowired
    private AlertJpaRepository alerts;

    @Autowired
    private UserJpaRepository users;

    private UUID reporterId;

    @BeforeEach
    void setUp() {
        alerts.deleteAll();
        users.deleteAll();

        User reporter = User.create("photo-ref@example.com", "hash", "Photo Ref");
        users.saveAndFlush(UserJpaEntity.from(reporter));
        reporterId = reporter.id().value();
    }

    @Test
    void returnsOnlyTheFilenamesSomeAlertPointsAt() {
        saveAlertWithPhoto("/api/v1/uploads/attached.jpg");
        saveAlertWithPhoto(null);

        Set<String> referenced = alerts.findReferencedPhotoFilenames(
            Set.of("attached.jpg", "orphan.jpg"));

        assertThat(referenced).containsExactly("attached.jpg");
    }

    @Test
    void returnsEmptyWhenNothingIsReferenced() {
        saveAlertWithPhoto(null);

        assertThat(alerts.findReferencedPhotoFilenames(Set.of("orphan-a.jpg", "orphan-b.jpg")))
            .isEmpty();
    }

    /**
     * The match is on the URL's suffix, so a filename that happens to be a suffix of a
     * *different* stored URL must not be reported as referenced — that would keep an
     * orphan forever, which is the harmless direction, but it would also mean the
     * predicate is looser than it looks.
     */
    @Test
    void doesNotMatchOnAPartialFilename() {
        saveAlertWithPhoto("/api/v1/uploads/photograph.jpg");

        assertThat(alerts.findReferencedPhotoFilenames(Set.of("graph.jpg"))).isEmpty();
        assertThat(alerts.findReferencedPhotoFilenames(Set.of("photograph.jpg")))
            .containsExactly("photograph.jpg");
    }

    @Test
    void handlesAnEmptyInputWithoutQuerying() {
        assertThat(alerts.findReferencedPhotoFilenames(Set.<String>of())).isEmpty();
    }

    private void saveAlertWithPhoto(String photoUrl) {
        Alert alert = new Alert(
            AlertId.generate(),
            "Alert " + UUID.randomUUID(),
            "description",
            AlertType.HAZARD,
            AlertSeverity.LOW,
            Location.of(42.695, 23.329),
            Instant.now(),
            AlertStatus.ACTIVE,
            reporterId,
            0, 0, 0, 0,
            null,
            photoUrl
        );
        alerts.saveAndFlush(AlertJpaEntity.from(alert));
    }
}
