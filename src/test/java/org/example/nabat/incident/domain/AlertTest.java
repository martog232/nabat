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

    /** A deadline for tests where its value does not matter, only that there is one. */
    private static final Instant DUE = Instant.parse("2026-11-19T10:00:00Z");

    @Test
    void shouldCreateAlertWithCorrectDefaultValues() {
        UUID reportedBy = UUID.randomUUID();

        Alert alert = Alert.create(
                "Test Alert",
                "Some description",
                1007,
                Location.of(42.0, 23.0),
                reportedBy,
                null, "LOZENETS", "LOZENETS"
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
                1007,
                location,
                reportedBy,
                null, "LOZENETS", "LOZENETS"
        );

        assertEquals("Pothole on Shipka", alert.title());
        assertEquals("Deep pothole outside number 12", alert.description());
        assertEquals(1007, alert.categoryCode());
        assertEquals(location, alert.location());
        assertEquals(reportedBy, alert.reportedBy());
    }

    @Test
    void shouldInitializeVoteCountsToZero() {
        Alert alert = Alert.create(
                "Test Alert",
                "Description",
                1001,
                Location.of(42.0, 23.0),
                UUID.randomUUID(),
                null, "LOZENETS", "LOZENETS"
        );

        assertEquals(0, alert.upvoteCount());
        assertEquals(0, alert.downvoteCount());
        assertEquals(0, alert.confirmationCount());
    }

    @Test
    void shouldGenerateUniqueIds() {
        Alert alert1 = Alert.create("A1", "D1", 1007, Location.of(42.0, 23.0), UUID.randomUUID(), null, "LOZENETS", "LOZENETS");
        Alert alert2 = Alert.create("A2", "D2", 1001, Location.of(42.0, 23.0), UUID.randomUUID(), null, "LOZENETS", "LOZENETS");

        assertNotEquals(alert1.id(), alert2.id());
    }

    @Nested
    class Lifecycle {

        private final Alert submitted = Alert.create(
                "T", "D", 1007,
                Location.of(42.69, 23.32), UUID.randomUUID(), null, "LOZENETS", "LOZENETS");

        @Test
        void walksTheForwardPathOneStepAtATime() {
            Alert alert = submitted.markSent(DUE);
            assertEquals(AlertStatus.SENT, alert.status());

            alert = alert.acknowledge();
            assertEquals(AlertStatus.ACKNOWLEDGED, alert.status());

            alert = alert.startWork();
            assertEquals(AlertStatus.IN_PROGRESS, alert.status());

            alert = alert.resolve(ClosedBy.REPORTER);
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
            assertThrows(IllegalStateException.class, () -> inProgress.markSent(DUE));
        }

        /**
         * The one move back: the new authority has received nothing, and what the old one
         * acknowledged or started was its own word. The district stays — the report has not moved.
         */
        @Test
        void aForwardGoesBackToSubmittedWithTheNewAuthority() {
            Alert forwarded = submitted.markSent(DUE).startWork().forwardTo("CGM");

            assertEquals(AlertStatus.SUBMITTED, forwarded.status());
            assertEquals("CGM", forwarded.authorityCode());
            assertEquals("LOZENETS", forwarded.districtCode());
        }

        /** Back to the authority that rejected it, which has it already: SENT, not SUBMITTED. */
        @Test
        void aReopenedRejectionGoesBackToSentWithItsAuthority() {
            Alert reopened = submitted.markSent(DUE).reject(ClosedBy.AUTHORITY).reopen(DUE);

            assertEquals(AlertStatus.SENT, reopened.status());
            assertEquals("LOZENETS", reopened.authorityCode());
            assertNull(reopened.closedBy());
            assertThrows(IllegalStateException.class, () -> submitted.reopen(DUE));
            assertThrows(IllegalStateException.class, () -> submitted.resolve(ClosedBy.REPORTER).reopen(DUE));
        }

        @Test
        void isNotForwardedWhenClosedOrToTheAuthorityItHas() {
            assertThrows(IllegalStateException.class, () -> submitted.resolve(ClosedBy.REPORTER).forwardTo("CGM"));
            assertThrows(IllegalArgumentException.class, () -> submitted.forwardTo("LOZENETS"));
            assertThrows(IllegalArgumentException.class, () -> submitted.forwardTo(" "));
        }

        /** The clock runs from delivery to the authority's first word, and no longer. */
        @Test
        void theDeadlineRunsWhileTheReportWaitsOnItsAuthority() {
            Alert sent = submitted.markSent(DUE);
            assertEquals(DUE, sent.answerDueAt());
            assertNull(submitted.answerDueAt());

            assertNull(sent.acknowledge().answerDueAt());
            assertNull(sent.startWork().answerDueAt());
            assertNull(sent.resolve(ClosedBy.REPORTER).answerDueAt());
            assertNull(sent.reject(ClosedBy.AUTHORITY).answerDueAt());
            // A forward stops the old authority's clock; the new one's starts on delivery.
            assertNull(sent.forwardTo("CGM").answerDueAt());
        }

        @Test
        void isOverdueOnlyPastTheDeadline() {
            Alert sent = submitted.markSent(DUE);

            assertFalse(sent.isOverdue(DUE.minusSeconds(1)));
            assertTrue(sent.isOverdue(DUE.plusSeconds(1)));
            assertFalse(sent.acknowledge().isOverdue(DUE.plusSeconds(1)));
        }

        /** Reopened after a disputed rejection: a full deadline again. */
        @Test
        void aReopenedReportGetsANewDeadline() {
            Instant later = DUE.plusSeconds(86_400);
            assertEquals(later, submitted.markSent(DUE).reject(ClosedBy.AUTHORITY).reopen(later).answerDueAt());
        }

        @Test
        void doesNotRepeatAStep() {
            assertThrows(IllegalStateException.class, () -> submitted.markSent(DUE).markSent(DUE));
        }

        /** A fix that happens before the institution answers must still be recordable. */
        @ParameterizedTest
        @EnumSource(value = AlertStatus.class, names = {"SUBMITTED", "SENT", "ACKNOWLEDGED", "IN_PROGRESS"})
        void canBeClosedFromAnyOpenState(AlertStatus open) {
            Alert alert = inStatus(open);

            assertEquals(AlertStatus.RESOLVED, alert.resolve(ClosedBy.REPORTER).status());
            assertEquals(AlertStatus.REJECTED, alert.reject(ClosedBy.MODERATOR).status());
        }

        @ParameterizedTest
        @EnumSource(value = AlertStatus.class, names = {"RESOLVED", "REJECTED"})
        void stopsMovingOnceClosed(AlertStatus closed) {
            Alert alert = inStatus(closed);

            assertFalse(closed.isOpen());
            assertThrows(IllegalStateException.class, () -> alert.resolve(ClosedBy.REPORTER));
            assertThrows(IllegalStateException.class, () -> alert.reject(ClosedBy.MODERATOR));
            assertThrows(IllegalStateException.class, alert::startWork);
        }

        @Test
        void aRejectionRecordsNoResolutionTime() {
            assertNull(submitted.reject(ClosedBy.MODERATOR).resolvedAt());
        }

        /** "The district says it is fixed" and "the reporter says so" must stay distinguishable. */
        @Test
        void recordsWhoClosedItAndNothingBeforeThat() {
            assertNull(submitted.closedBy());
            assertNull(submitted.startWork().closedBy());
            assertEquals(ClosedBy.AUTHORITY, submitted.resolve(ClosedBy.AUTHORITY).closedBy());
            assertEquals(ClosedBy.MODERATOR, submitted.reject(ClosedBy.MODERATOR).closedBy());
        }

        @Test
        void refusesToCloseWithoutSayingWho() {
            assertThrows(IllegalArgumentException.class, () -> submitted.resolve(null));
            assertThrows(IllegalArgumentException.class, () -> submitted.reject(null));
        }

        private Alert inStatus(AlertStatus status) {
            return switch (status) {
                case SUBMITTED -> submitted;
                case SENT -> submitted.markSent(DUE);
                case ACKNOWLEDGED -> submitted.acknowledge();
                case IN_PROGRESS -> submitted.startWork();
                case RESOLVED -> submitted.resolve(ClosedBy.REPORTER);
                case REJECTED -> submitted.reject(ClosedBy.MODERATOR);
            };
        }
    }
}
