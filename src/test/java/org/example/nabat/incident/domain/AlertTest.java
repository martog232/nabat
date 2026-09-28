package org.example.nabat.incident.domain;

import org.example.nabat.shared.domain.Location;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

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
                AlertType.POTHOLE,
                Location.of(42.0, 23.0),
                reportedBy,
                null, "LOZENETS"
        );

        assertNotNull(alert.id());
        assertNotNull(alert.id().value());
        assertNotNull(alert.createdAt());
        assertEquals(AlertStatus.SUBMITTED, alert.status());
        assertTrue(alert.createdAt().isBefore(Instant.now().plusSeconds(1)));
    }

    @Test
    void shouldCreateAlertWithAllProvidedFields() {
        UUID reportedBy = UUID.randomUUID();
        Location location = Location.of(42.6936, 23.3337);

        Alert alert = Alert.create(
                "Pothole on Shipka",
                "Deep pothole outside number 12",
                AlertType.POTHOLE,
                location,
                reportedBy,
                null, "LOZENETS"
        );

        assertEquals("Pothole on Shipka", alert.title());
        assertEquals("Deep pothole outside number 12", alert.description());
        assertEquals(AlertType.POTHOLE, alert.type());
        assertEquals(location, alert.location());
        assertEquals(reportedBy, alert.reportedBy());
    }

    @Test
    void shouldInitializeVoteCountsToZero() {
        Alert alert = Alert.create(
                "Test Alert",
                "Description",
                AlertType.WASTE,
                Location.of(42.0, 23.0),
                UUID.randomUUID(),
                null, "LOZENETS"
        );

        assertEquals(0, alert.upvoteCount());
        assertEquals(0, alert.downvoteCount());
        assertEquals(0, alert.confirmationCount());
    }

    @Test
    void shouldGenerateUniqueIds() {
        Alert alert1 = Alert.create("A1", "D1", AlertType.POTHOLE, Location.of(42.0, 23.0), UUID.randomUUID(), null, "LOZENETS");
        Alert alert2 = Alert.create("A2", "D2", AlertType.WASTE, Location.of(42.0, 23.0), UUID.randomUUID(), null, "LOZENETS");

        assertNotEquals(alert1.id(), alert2.id());
    }

    @Nested
    class Lifecycle {

        private final Alert submitted = Alert.create(
                "T", "D", AlertType.POTHOLE,
                Location.of(42.69, 23.32), UUID.randomUUID(), null, "LOZENETS");

        @Test
        void walksTheForwardPathOneStepAtATime() {
            Alert alert = submitted.markSent();
            assertEquals(AlertStatus.SENT, alert.status());

            alert = alert.acknowledge();
            assertEquals(AlertStatus.ACKNOWLEDGED, alert.status());

            alert = alert.startWork();
            assertEquals(AlertStatus.IN_PROGRESS, alert.status());

            alert = alert.resolve();
            assertEquals(AlertStatus.RESOLVED, alert.status());
            assertNotNull(alert.resolvedAt());
        }

        /** An institution that starts work on something it saw on its own page has not waited for the email. */
        @Test
        void mayMoveForwardPastAStepThatWasSkipped() {
            assertEquals(AlertStatus.IN_PROGRESS, submitted.startWork().status());
        }

        @Test
        void neverMovesBackwards() {
            Alert inProgress = submitted.startWork();

            assertThrows(IllegalStateException.class, inProgress::acknowledge);
            assertThrows(IllegalStateException.class, inProgress::markSent);
        }

        @Test
        void doesNotRepeatAStep() {
            assertThrows(IllegalStateException.class, submitted.markSent()::markSent);
        }

        /** A fix that happens before the institution answers must still be recordable. */
        @ParameterizedTest
        @EnumSource(value = AlertStatus.class, names = {"SUBMITTED", "SENT", "ACKNOWLEDGED", "IN_PROGRESS"})
        void canBeClosedFromAnyOpenState(AlertStatus open) {
            Alert alert = inStatus(open);

            assertEquals(AlertStatus.RESOLVED, alert.resolve().status());
            assertEquals(AlertStatus.REJECTED, alert.reject().status());
        }

        @ParameterizedTest
        @EnumSource(value = AlertStatus.class, names = {"RESOLVED", "REJECTED"})
        void stopsMovingOnceClosed(AlertStatus closed) {
            Alert alert = inStatus(closed);

            assertFalse(closed.isOpen());
            assertThrows(IllegalStateException.class, alert::resolve);
            assertThrows(IllegalStateException.class, alert::reject);
            assertThrows(IllegalStateException.class, alert::startWork);
        }

        @Test
        void aRejectionRecordsNoResolutionTime() {
            assertNull(submitted.reject().resolvedAt());
        }

        private Alert inStatus(AlertStatus status) {
            return switch (status) {
                case SUBMITTED -> submitted;
                case SENT -> submitted.markSent();
                case ACKNOWLEDGED -> submitted.acknowledge();
                case IN_PROGRESS -> submitted.startWork();
                case RESOLVED -> submitted.resolve();
                case REJECTED -> submitted.reject();
            };
        }
    }
}
