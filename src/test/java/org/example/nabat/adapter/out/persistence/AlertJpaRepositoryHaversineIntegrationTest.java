package org.example.nabat.adapter.out.persistence;

import org.example.nabat.domain.model.Alert;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.AlertSeverity;
import org.example.nabat.domain.model.AlertStatus;
import org.example.nabat.domain.model.AlertType;
import org.example.nabat.domain.model.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class AlertJpaRepositoryHaversineIntegrationTest {

    private static final double SOFIA_LAT = 42.695;
    private static final double SOFIA_LON = 23.329;

    @Autowired
    private AlertJpaRepository repository;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
    }

    @Test
    void findActiveAlertsWithinRadiusReturnsOnlyActiveNearbyAlertsNewestFirst() {
        Alert oldNearby = alert("Old nearby", AlertStatus.ACTIVE, SOFIA_LAT, SOFIA_LON, Instant.parse("2026-05-06T08:00:00Z"));
        Alert newNearby = alert("New nearby", AlertStatus.ACTIVE, 42.697, 23.331, Instant.parse("2026-05-06T09:00:00Z"));
        Alert farAway = alert("Far away", AlertStatus.ACTIVE, 42.1354, 24.7453, Instant.parse("2026-05-06T10:00:00Z"));
        Alert resolvedNearby = alert("Resolved nearby", AlertStatus.RESOLVED, SOFIA_LAT, SOFIA_LON, Instant.parse("2026-05-06T11:00:00Z"));
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
        Alert sameSpot = alert("Same spot", AlertStatus.ACTIVE, SOFIA_LAT, SOFIA_LON, Instant.parse("2026-05-06T08:00:00Z"));
        repository.saveAndFlush(AlertJpaEntity.from(sameSpot));

        List<AlertJpaEntity> result = repository.findActiveAlertsWithinRadius(SOFIA_LAT, SOFIA_LON, 0.0);

        assertThat(result)
            .extracting(AlertJpaEntity::getTitle)
            .containsExactly("Same spot");
    }

    private static Alert alert(String title, AlertStatus status, double latitude, double longitude, Instant createdAt) {
        return new Alert(
            AlertId.generate(),
            title,
            "Integration query test",
            AlertType.FIRE,
            AlertSeverity.MEDIUM,
            Location.of(latitude, longitude),
            createdAt,
            status,
            UUID.randomUUID(),
            0,
            0,
            0,
            status == AlertStatus.RESOLVED ? createdAt.plusSeconds(60) : null
        );
    }
}

