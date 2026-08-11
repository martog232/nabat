package org.example.nabat.incident.adapter.out.persistence;

import org.example.nabat.identity.adapter.out.persistence.UserJpaEntity;
import org.example.nabat.PostgresTestSupport;
import org.example.nabat.identity.adapter.out.persistence.UserJpaRepository;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertSeverity;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.AlertType;
import org.example.nabat.shared.domain.Location;
import org.example.nabat.identity.domain.Role;
import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class AlertJpaRepositoryPostgisIntegrationTest extends PostgresTestSupport {

    private static final double SOFIA_LAT = 42.695;
    private static final double SOFIA_LON = 23.329;

    @Autowired
    private AlertJpaRepository repository;

    @Autowired
    private UserJpaRepository userRepository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void findActiveAlertsWithinRadiusReturnsOnlyActiveNearbyAlertsNewestFirst() {
        Alert oldNearby = alert("Old nearby", AlertStatus.ACTIVE, SOFIA_LAT, SOFIA_LON, Instant.parse("2026-05-06T08:00:00Z"), seedUser("old-nearby"));
        Alert newNearby = alert("New nearby", AlertStatus.ACTIVE, 42.697, 23.331, Instant.parse("2026-05-06T09:00:00Z"), seedUser("new-nearby"));
        Alert farAway = alert("Far away", AlertStatus.ACTIVE, 42.1354, 24.7453, Instant.parse("2026-05-06T10:00:00Z"), seedUser("far-away"));
        Alert resolvedNearby = alert("Resolved nearby", AlertStatus.RESOLVED, SOFIA_LAT, SOFIA_LON, Instant.parse("2026-05-06T11:00:00Z"), seedUser("resolved-nearby"));
        repository.saveAll(List.of(
            AlertJpaEntity.from(oldNearby),
            AlertJpaEntity.from(newNearby),
            AlertJpaEntity.from(farAway),
            AlertJpaEntity.from(resolvedNearby)
        ));
        repository.flush();

        List<AlertJpaEntity> result =
            repository.findActiveAlertsWithinRadius(SOFIA_LAT, SOFIA_LON, 5.0, null, null, 100);

        assertThat(result)
            .extracting(AlertJpaEntity::getTitle)
            .containsExactly("New nearby", "Old nearby");
    }

    @Test
    void findActiveAlertsWithinRadiusAppliesTheLimitInSqlKeepingTheNewest() {
        UserId reporter = seedUser("limited");
        repository.saveAll(List.of(
            AlertJpaEntity.from(alert("Oldest", AlertStatus.ACTIVE, SOFIA_LAT, SOFIA_LON,
                Instant.parse("2026-05-06T08:00:00Z"), reporter)),
            AlertJpaEntity.from(alert("Middle", AlertStatus.ACTIVE, SOFIA_LAT, SOFIA_LON,
                Instant.parse("2026-05-06T09:00:00Z"), reporter)),
            AlertJpaEntity.from(alert("Newest", AlertStatus.ACTIVE, SOFIA_LAT, SOFIA_LON,
                Instant.parse("2026-05-06T10:00:00Z"), reporter))
        ));
        repository.flush();

        List<AlertJpaEntity> result =
            repository.findActiveAlertsWithinRadius(SOFIA_LAT, SOFIA_LON, 5.0, null, null, 2);

        // Newest first, oldest dropped — the right end to lose on a live incident map.
        assertThat(result)
            .extracting(AlertJpaEntity::getTitle)
            .containsExactly("Newest", "Middle");
    }

    @Test
    void findActiveAlertsWithinRadiusFiltersByTypeAndSeverity() {
        UserId reporter = seedUser("filtered");
        AlertJpaEntity fire = AlertJpaEntity.from(new Alert(
            AlertId.generate(), "Fire", "d", AlertType.FIRE, AlertSeverity.CRITICAL,
            Location.of(SOFIA_LAT, SOFIA_LON), Instant.parse("2026-05-06T10:00:00Z"),
            AlertStatus.ACTIVE, reporter.value(), 0, 0, 0, 0, null, null));
        AlertJpaEntity hazard = AlertJpaEntity.from(new Alert(
            AlertId.generate(), "Hazard", "d", AlertType.HAZARD, AlertSeverity.LOW,
            Location.of(SOFIA_LAT, SOFIA_LON), Instant.parse("2026-05-06T11:00:00Z"),
            AlertStatus.ACTIVE, reporter.value(), 0, 0, 0, 0, null, null));
        repository.saveAll(List.of(fire, hazard));
        repository.flush();

        assertThat(repository.findActiveAlertsWithinRadius(
                SOFIA_LAT, SOFIA_LON, 5.0, "FIRE", null, 100))
            .extracting(AlertJpaEntity::getTitle)
            .containsExactly("Fire");

        assertThat(repository.findActiveAlertsWithinRadius(
                SOFIA_LAT, SOFIA_LON, 5.0, null, "LOW", 100))
            .extracting(AlertJpaEntity::getTitle)
            .containsExactly("Hazard");

        // Both null means no filter at all. This is the case the `CAST(:type AS text) IS
        // NULL` form exists for: PostgreSQL cannot infer a bare parameter's type in a NULL
        // comparison and fails the statement outright, so the unfiltered path is exactly
        // the one that breaks if the cast is dropped.
        assertThat(repository.findActiveAlertsWithinRadius(
                SOFIA_LAT, SOFIA_LON, 5.0, null, null, 100))
            .hasSize(2);
    }

    @Test
    void findActiveAlertsWithinRadiusIncludesAlertAtSameCoordinatesWithZeroRadius() {
        Alert sameSpot = alert("Same spot", AlertStatus.ACTIVE, SOFIA_LAT, SOFIA_LON, Instant.parse("2026-05-06T08:00:00Z"), seedUser("same-spot"));
        repository.saveAndFlush(AlertJpaEntity.from(sameSpot));

        List<AlertJpaEntity> result =
            repository.findActiveAlertsWithinRadius(SOFIA_LAT, SOFIA_LON, 0.0, null, null, 100);

        assertThat(result)
            .extracting(AlertJpaEntity::getTitle)
            .containsExactly("Same spot");
    }

    private UserId seedUser(String label) {
        User user = new User(
            UserId.generate(),
            label + "@example.com",
            "hashed-password",
            "Test User " + label,
            Role.USER,
            true,
            true,
            Instant.now(),
            Instant.now(),
            5,
            null,
            null,
            null
        ,

            0);
        userRepository.saveAndFlush(UserJpaEntity.from(user));
        return user.id();
    }

    private static Alert alert(String title, AlertStatus status, double latitude, double longitude, Instant createdAt, UserId reporterId) {
        return new Alert(
            AlertId.generate(),
            title,
            "Integration query test",
            AlertType.FIRE,
            AlertSeverity.MEDIUM,
            Location.of(latitude, longitude),
            createdAt,
            status,
            reporterId.value(),
            0,
            0,
            0,

            0,
            status == AlertStatus.RESOLVED ? createdAt.plusSeconds(60) : null,
            null
        );
    }
}
