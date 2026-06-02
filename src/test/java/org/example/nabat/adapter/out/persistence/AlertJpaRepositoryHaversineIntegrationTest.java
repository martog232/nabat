package org.example.nabat.adapter.out.persistence;

import org.example.nabat.domain.model.Alert;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.AlertSeverity;
import org.example.nabat.domain.model.AlertStatus;
import org.example.nabat.domain.model.AlertType;
import org.example.nabat.domain.model.Location;
import org.example.nabat.domain.model.Role;
import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.UserId;
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
class AlertJpaRepositoryPostgisIntegrationTest extends PostgisIntegrationTestSupport {

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

        List<AlertJpaEntity> result = repository.findActiveAlertsWithinRadius(SOFIA_LAT, SOFIA_LON, 5.0);

        assertThat(result)
            .extracting(AlertJpaEntity::getTitle)
            .containsExactly("New nearby", "Old nearby");
    }

    @Test
    void findActiveAlertsWithinRadiusIncludesAlertAtSameCoordinatesWithZeroRadius() {
        Alert sameSpot = alert("Same spot", AlertStatus.ACTIVE, SOFIA_LAT, SOFIA_LON, Instant.parse("2026-05-06T08:00:00Z"), seedUser("same-spot"));
        repository.saveAndFlush(AlertJpaEntity.from(sameSpot));

        List<AlertJpaEntity> result = repository.findActiveAlertsWithinRadius(SOFIA_LAT, SOFIA_LON, 0.0);

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
        );
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
            status == AlertStatus.RESOLVED ? createdAt.plusSeconds(60) : null
        );
    }
}
