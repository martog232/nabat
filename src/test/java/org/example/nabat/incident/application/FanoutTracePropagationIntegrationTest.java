package org.example.nabat.incident.application;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.example.nabat.PostgresTestSupport;
import org.example.nabat.identity.adapter.out.persistence.UserJpaEntity;
import org.example.nabat.identity.adapter.out.persistence.UserJpaRepository;
import org.example.nabat.identity.application.port.out.EmailSender;
import org.example.nabat.incident.adapter.out.persistence.AlertJpaRepository;
import org.example.nabat.incident.application.port.in.CreateAlertUseCase;
import org.example.nabat.incident.application.port.out.AlertAudiencePort;
import org.example.nabat.incident.application.port.out.AlertNotificationPort;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertSeverity;
import org.example.nabat.incident.domain.AlertType;
import org.example.nabat.testsupport.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

/**
 * Asserts that a request's trace survives the hop onto the fan-out thread.
 *
 * <h2>Why this is worth a test</h2>
 * {@code NewAlertFanout} is {@code @Async}, so it runs on a pool thread with its own
 * thread-locals. Tracing context and the MDC that {@code logging.pattern.console} reads
 * ({@code %X{traceId}}) are thread-locals. Without a {@link
 * org.springframework.core.task.TaskDecorator} that carries them across, every log line
 * the fan-out writes comes out with an empty trace — and the fan-out is precisely the
 * part of a request that is hardest to follow by reading code, so it is the part that
 * most needs to be followable in Grafana.
 *
 * <p>Nothing else catches this. The failure is invisible in tests, invisible in an HTTP
 * response, and shows up only as a trace that stops at the controller in an environment
 * where somebody is already trying to debug something else.
 *
 * <p>Note the deliberate limit of the guarantee: this covers the in-process hop from the
 * publishing thread to the listener thread. A publication replayed at startup by the
 * Event Publication Registry has no request to belong to and legitimately begins a new
 * trace; see {@code AlertCreatedOutboxIntegrationTest} for that path.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class FanoutTracePropagationIntegrationTest extends PostgresTestSupport {

    private static final UUID RECIPIENT = UUID.randomUUID();

    @Autowired
    private CreateAlertUseCase createAlert;

    @Autowired
    private Tracer tracer;

    @Autowired
    private ObservationRegistry observations;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private UserJpaRepository users;

    @Autowired
    private AlertJpaRepository alerts;

    @MockBean
    private AlertAudiencePort audiencePort;

    /** Prevent real SMTP during integration tests. */
    @MockBean
    private EmailSender emailSender;

    /**
     * A real bean rather than a mock: it has to read thread-locals from inside the
     * listener thread, which is exactly what a Mockito stub cannot do for us.
     */
    @Autowired
    private TraceRecordingNotificationPort notificationPort;

    private UUID reporterId;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM event_publication");
        alerts.deleteAll();
        users.deleteAll();
        notificationPort.observed.clear();

        when(audiencePort.recipientsFor(any(), any(), anyDouble())).thenReturn(List.of(RECIPIENT));

        var reporter = Fixtures.user("trace-reporter@example.com");
        users.save(UserJpaEntity.from(reporter));
        reporterId = reporter.id().value();
    }

    @Test
    void theFanOutRunsInsideTheTraceThatCreatedTheAlert() throws Exception {
        String expectedTraceId = createAlertWithinAnObservation();

        Observed observed = notificationPort.observed.poll(10, TimeUnit.SECONDS);
        assertNotNull(observed, "the fan-out never ran");

        assertNotNull(
            observed.traceId(),
            "the fan-out thread has no tracing context: the request's trace ends at the "
            + "publishing thread, so every log line and span from the fan-out is orphaned"
        );
        assertEquals(
            expectedTraceId,
            observed.traceId(),
            "the fan-out ran under a different trace than the request that caused it"
        );
    }

    @Test
    void theFanOutLogsCarryTheRequestsTraceId() throws Exception {
        String expectedTraceId = createAlertWithinAnObservation();

        Observed observed = notificationPort.observed.poll(10, TimeUnit.SECONDS);
        assertNotNull(observed, "the fan-out never ran");

        // Separate from the assertion above: the tracer can hold a span while the MDC
        // stays empty, and it is the MDC that logging.pattern.console reads. A trace
        // visible to Zipkin but absent from the logs still breaks log-to-trace pivoting.
        assertEquals(
            expectedTraceId,
            observed.mdcTraceId(),
            "the fan-out's log lines would print an empty traceId, so its logs cannot be "
            + "correlated with the request in Loki or Grafana"
        );
    }

    /**
     * Creates an alert the way an HTTP request does: inside an {@link Observation}.
     *
     * <p>Not {@code tracer.withSpan(...)}, which is the obvious thing to write and is
     * wrong here. Only {@code ObservationThreadLocalAccessor} is registered through the
     * ServiceLoader — {@code micrometer-tracing} ships an
     * {@code ObservationAwareSpanThreadLocalAccessor} but registers none — so context
     * propagation carries an <em>Observation</em>, and a bare span opened outside one
     * travels nowhere. Spring MVC wraps every request in an Observation, so this is what
     * the production path actually looks like; a test built on a bare span fails even
     * when propagation is configured correctly.
     *
     * @return the trace id the fan-out is expected to inherit
     */
    private String createAlertWithinAnObservation() {
        Observation observation = Observation.start("create-alert-request", observations);
        try (Observation.Scope ignored = observation.openScope()) {
            Span current = tracer.currentSpan();
            assertNotNull(current, "no span inside the observation; tracing is not wired in this context");
            createAlert.createAlert(command());
            return current.context().traceId();
        } finally {
            observation.stop();
        }
    }

    private CreateAlertUseCase.CreateAlertCommand command() {
        return new CreateAlertUseCase.CreateAlertCommand(
            "Trace check",
            "raised by " + FanoutTracePropagationIntegrationTest.class.getSimpleName(),
            AlertType.HAZARD,
            AlertSeverity.HIGH,
            42.6977,
            23.3219,
            reporterId,
            null
        );
    }

    /** What the listener thread could see when it ran. */
    record Observed(String traceId, String mdcTraceId) {}

    static class TraceRecordingNotificationPort implements AlertNotificationPort {

        private final Tracer tracer;
        final BlockingQueue<Observed> observed = new LinkedBlockingQueue<>();

        TraceRecordingNotificationPort(Tracer tracer) {
            this.tracer = tracer;
        }

        @Override
        public void broadcastAlert(Alert alert, List<UUID> userIds) {
            Span current = tracer.currentSpan();
            observed.add(new Observed(
                current == null ? null : current.context().traceId(),
                MDC.get("traceId")
            ));
        }

        @Override
        public void broadcastAlertUpdate(Alert alert) {
            // not part of the creation fan-out
        }
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class Config {

        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        TraceRecordingNotificationPort traceRecordingNotificationPort(Tracer tracer) {
            return new TraceRecordingNotificationPort(tracer);
        }
    }
}
