package org.example.nabat.domain.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AlertTest {

    @Test
    void shouldCreateAlertWithCorrectDefaultValues() {
        UUID reportedBy = UUID.randomUUID();

        Alert alert = Alert.create(
                "Test Alert",
                "Some description",
                AlertType.FIRE,
                AlertSeverity.HIGH,
                Location.of(42.0, 23.0),
                reportedBy
        );

        assertNotNull(alert.id());
        assertNotNull(alert.id().value());
        assertNotNull(alert.createdAt());
        assertEquals(AlertStatus.ACTIVE, alert.status());
        assertTrue(alert.createdAt().isBefore(Instant.now().plusSeconds(1)));
    }

    @Test
    void shouldCreateAlertWithAllProvidedFields() {
        UUID reportedBy = UUID.randomUUID();
        Location location = Location.of(51.5, -0.12);

        Alert alert = Alert.create(
                "London Fire",
                "Fire in central London",
                AlertType.FIRE,
                AlertSeverity.CRITICAL,
                location,
                reportedBy
        );

        assertEquals("London Fire", alert.title());
        assertEquals("Fire in central London", alert.description());
        assertEquals(AlertType.FIRE, alert.type());
        assertEquals(AlertSeverity.CRITICAL, alert.severity());
        assertEquals(location, alert.location());
        assertEquals(reportedBy, alert.reportedBy());
    }

    @Test
    void shouldInitializeVoteCountsToZero() {
        Alert alert = Alert.create(
                "Test Alert",
                "Description",
                AlertType.CRIME,
                AlertSeverity.LOW,
                Location.of(42.0, 23.0),
                UUID.randomUUID()
        );

        assertEquals(0, alert.upvoteCount());
        assertEquals(0, alert.downvoteCount());
        assertEquals(0, alert.confirmationCount());
    }

    @Test
    void shouldGenerateUniqueIds() {
        Alert alert1 = Alert.create("A1", "D1", AlertType.FIRE, AlertSeverity.HIGH, Location.of(42.0, 23.0), UUID.randomUUID());
        Alert alert2 = Alert.create("A2", "D2", AlertType.CRIME, AlertSeverity.LOW, Location.of(42.0, 23.0), UUID.randomUUID());

        assertNotEquals(alert1.id(), alert2.id());
    }
}
