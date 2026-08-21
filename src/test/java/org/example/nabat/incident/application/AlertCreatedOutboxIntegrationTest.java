package org.example.nabat.incident.application;

import org.example.nabat.PostgresTestSupport;
import org.example.nabat.identity.adapter.out.persistence.UserJpaEntity;
import org.example.nabat.identity.adapter.out.persistence.UserJpaRepository;
import org.example.nabat.identity.application.port.out.EmailSender;
import org.example.nabat.incident.adapter.out.persistence.AlertJpaRepository;
import org.example.nabat.incident.application.port.in.CreateAlertUseCase;
import org.example.nabat.incident.application.port.out.AlertAudiencePort;
import org.example.nabat.incident.application.port.out.AlertNotificationPort;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertCreated;
import org.example.nabat.incident.domain.AlertSeverity;
import org.example.nabat.incident.domain.AlertType;
import org.example.nabat.testsupport.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Proves the alert fan-out is durable, not merely asynchronous.
 *
 * <p>{@code NewAlertFanoutWiringTest} shows the listener runs after commit and off the
 * publishing thread. It cannot show what happens when the listener <em>fails</em>: before
 * the Event Publication Registry the answer was "nothing" — the exception surfaced in a
 * log line on an async thread and the push was gone, with no record that it had been owed.
 *
 * <p>These tests assert against the {@code event_publication} table directly rather than
 * through an abstraction, because the table <em>is</em> the guarantee: a row written in
 * the publishing transaction, completed only once the listener returns. A crash is
 * simulated by making the delivery port throw, which leaves exactly the state a crash
 * would leave behind — an outstanding row that startup replay picks up.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class AlertCreatedOutboxIntegrationTest extends PostgresTestSupport {

    /** How long an assertion waits on the async listener before failing. */
    private static final Duration DELIVERY_TIMEOUT = Duration.ofSeconds(10);

    private static final UUID RECIPIENT = UUID.randomUUID();

    @Autowired
    private CreateAlertUseCase createAlert;

    @Autowired
    private IncompleteEventPublications incompletePublications;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserJpaRepository users;

    @Autowired
    private AlertJpaRepository alerts;

    /**
     * Mocked so the test controls whether delivery succeeds. This is the seam that stands
     * in for a crash between commit and push.
     */
    @MockBean
    private AlertNotificationPort notificationPort;

    /** Mocked so there is always an audience; the real adapter would need seeded subscriptions. */
    @MockBean
    private AlertAudiencePort audiencePort;

    /** Prevent real SMTP during integration tests. */
    @MockBean
    private EmailSender emailSender;

    private UUID reporterId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM event_publication");
        alerts.deleteAll();
        users.deleteAll();

        Mockito.reset(notificationPort, audiencePort);
        when(audiencePort.recipientsFor(any(), any(), anyDouble())).thenReturn(List.of(RECIPIENT));

        var reporter = Fixtures.user("outbox-reporter@example.com");
        users.save(UserJpaEntity.from(reporter));
        reporterId = reporter.id().value();
    }

    @Test
    void aDeliveredEventLeavesACompletedPublicationBehind() {
        createAlert.createAlert(command("Delivered"));

        verify(notificationPort, timeout(DELIVERY_TIMEOUT.toMillis()))
            .broadcastAlert(any(Alert.class), any());

        // The registry records one row per (event, listener), stamped complete once the
        // listener returns. `update` completion mode keeps the row rather than deleting it.
        await(() -> outstandingPublications() == 0);
        assertEquals(1, totalPublications(), "the publication was never recorded at all");
    }

    @Test
    void aFailedDeliveryLeavesThePublicationOutstanding() {
        doThrow(new IllegalStateException("socket write failed"))
            .when(notificationPort).broadcastAlert(any(Alert.class), any());

        createAlert.createAlert(command("Lost push"));

        verify(notificationPort, timeout(DELIVERY_TIMEOUT.toMillis()))
            .broadcastAlert(any(Alert.class), any());

        // The point of the whole change: the failure is not silent. Without the registry
        // this row would not exist and the alert would simply never have been pushed.
        await(() -> outstandingPublications() == 1);

        String serialized = jdbc.queryForObject(
            "SELECT serialized_event FROM event_publication WHERE completion_date IS NULL",
            String.class
        );
        assertNotNull(serialized);
        assertTrue(
            serialized.contains("Lost push"),
            "the outstanding row does not carry the event payload, so replay cannot rebuild it: " + serialized
        );
    }

    @Test
    void anOutstandingPublicationIsRedeliveredOnResubmit() {
        doThrow(new IllegalStateException("socket write failed"))
            .doNothing()
            .when(notificationPort).broadcastAlert(any(Alert.class), any());

        createAlert.createAlert(command("Replayed"));
        await(() -> outstandingPublications() == 1);

        // What republish-outstanding-events-on-restart does at startup, invoked directly:
        // the same replay path, without restarting the context.
        incompletePublications.resubmitIncompletePublications(publication -> true);

        verify(notificationPort, timeout(DELIVERY_TIMEOUT.toMillis()).times(2))
            .broadcastAlert(any(Alert.class), any());
        await(() -> outstandingPublications() == 0);
    }

    /**
     * Replay deserialises the stored JSON back into an {@link AlertCreated}, so a payload
     * that serialises but does not round-trip would turn every outstanding row into a
     * permanent failure — visible only after a crash, which is the worst time to find out.
     */
    @Test
    void theStoredPayloadRoundTripsBackIntoTheHandler() {
        doThrow(new IllegalStateException("socket write failed"))
            .doNothing()
            .when(notificationPort).broadcastAlert(any(Alert.class), any());

        Alert created = createAlert.createAlert(command("Round trip"));
        await(() -> outstandingPublications() == 1);

        incompletePublications.resubmitIncompletePublications(publication -> true);

        ArgumentCaptor<Alert> rebuilt = ArgumentCaptor.forClass(Alert.class);
        verify(notificationPort, timeout(DELIVERY_TIMEOUT.toMillis()).times(2))
            .broadcastAlert(rebuilt.capture(), any());

        // Equality across the whole record, not just the id: an Instant that loses
        // precision or a Location that comes back transposed would still "work" here
        // without it.
        assertEquals(created, rebuilt.getAllValues().get(1));
    }

    private CreateAlertUseCase.CreateAlertCommand command(String title) {
        return new CreateAlertUseCase.CreateAlertCommand(
            title,
            "raised by " + AlertCreatedOutboxIntegrationTest.class.getSimpleName(),
            AlertType.HAZARD,
            AlertSeverity.HIGH,
            42.6977,
            23.3219,
            reporterId,
            null
        );
    }

    private int outstandingPublications() {
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM event_publication WHERE completion_date IS NULL", Integer.class);
        return count == null ? 0 : count;
    }

    private int totalPublications() {
        Integer count = jdbc.queryForObject("SELECT count(*) FROM event_publication", Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * Polls rather than sleeps: the listener is async and completion happens in its own
     * transaction, so there is no handle to join on from here.
     */
    private void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + DELIVERY_TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while awaiting the fan-out", e);
            }
        }
        throw new AssertionError(
            "condition never held: " + totalPublications() + " publication(s), "
            + outstandingPublications() + " outstanding"
        );
    }
}
