package org.example.nabat.jurisdiction.application;

import org.example.nabat.PostgresTestSupport;
import org.example.nabat.identity.adapter.out.persistence.UserJpaEntity;
import org.example.nabat.identity.adapter.out.persistence.UserJpaRepository;
import org.example.nabat.identity.application.port.out.EmailSender;
import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.adapter.out.persistence.AlertJpaRepository;
import org.example.nabat.incident.application.port.in.CreateAlertUseCase;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.jurisdiction.application.port.out.SignalMailer;
import org.example.nabat.jurisdiction.application.port.out.SignalMailer.SignalMail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.modulith.events.IncompleteEventPublications;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * A report created in a district reaches that district, once, and is marked {@code SENT}.
 *
 * <p>End to end through the real listener, the real publication registry and the real
 * migrations — the mailer is the only stand-in, because what is under test is what nabat
 * does around a send, not SMTP. The replay cases assert on the tables rather than through
 * an abstraction, for the same reason as {@code AlertCreatedOutboxIntegrationTest}: the
 * rows are the guarantee.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class SignalDispatchIntegrationTest extends PostgresTestSupport {

    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    /** Studentski grad — squarely inside Studentski district. */
    private static final double LAT = 42.6515;
    private static final double LON = 23.3455;

    private static final String DISPATCHER = "listener_id LIKE '%SignalDispatcher%'";

    @Autowired private CreateAlertUseCase createAlert;
    @Autowired private IncompleteEventPublications incompletePublications;
    @Autowired private AlertJpaRepository alerts;
    @Autowired private UserJpaRepository users;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private SignalMailer mailer;
    @MockitoBean private EmailSender accountMail;

    private User reporter;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM event_publication");
        jdbc.update("DELETE FROM signal_dispatches");
        alerts.deleteAllInBatch();
        users.deleteAll();
        Mockito.reset(mailer);

        reporter = User.create("reporter@example.com", "hash", "Мария Иванова");
        users.saveAndFlush(UserJpaEntity.from(reporter));
    }

    @Test
    void aNewReportIsMailedToItsDistrictAndMarkedSent() {
        Alert created = report("Счупена шахта");

        ArgumentCaptor<SignalMail> mail = ArgumentCaptor.forClass(SignalMail.class);
        verify(mailer, timeout(TIMEOUT.toMillis())).send(mail.capture());
        assertThat(mail.getValue().to()).isEqualTo("studentski@rayon.nabat.test");
        assertThat(mail.getValue().districtName()).isEqualTo("Район Студентски");
        assertThat(mail.getValue().reporterEmail()).isEqualTo("reporter@example.com");

        await(() -> status(created.id().value()) == AlertStatus.SENT);
        assertThat(dispatchesFor(created.id().value())).isEqualTo(1);
        assertThat(outstandingDispatches()).isZero();
    }

    /**
     * The whole point of doing this through the registry. The send fails, the report stays
     * SUBMITTED with its publication outstanding, and the resubmission — what a restart does
     * — delivers it.
     */
    @Test
    void aFailedSendIsRetriedUntilTheReportIsDelivered() {
        doThrow(new MailSendException("SMTP down"))
            .doNothing()
            .when(mailer).send(any());

        Alert created = report("Изхвърлени отпадъци");

        await(() -> outstandingDispatches() == 1);
        assertThat(status(created.id().value())).isEqualTo(AlertStatus.SUBMITTED);
        assertThat(dispatchesFor(created.id().value())).isZero();

        incompletePublications.resubmitIncompletePublications(publication -> true);

        await(() -> status(created.id().value()) == AlertStatus.SENT);
        verify(mailer, Mockito.times(2)).send(any());
        assertThat(dispatchesFor(created.id().value())).isEqualTo(1);
    }

    /**
     * A replay of a delivery that already happened — the crash came after the commit that
     * recorded it, but before the registry marked the listener done. The district must not
     * get the report twice.
     */
    @Test
    void aReplayOfADeliveredReportDoesNotMailTheDistrictAgain() {
        Alert created = report("Неработеща лампа");
        await(() -> status(created.id().value()) == AlertStatus.SENT);

        jdbc.update("UPDATE event_publication SET completion_date = NULL, status = 'PUBLISHED' WHERE " + DISPATCHER);
        incompletePublications.resubmitIncompletePublications(publication -> true);

        await(() -> outstandingDispatches() == 0);
        verify(mailer, Mockito.times(1)).send(any());
        assertThat(dispatchesFor(created.id().value())).isEqualTo(1);
    }

    private Alert report(String title) {
        return createAlert.createAlert(new CreateAlertUseCase.CreateAlertCommand(
            title, "raised by " + getClass().getSimpleName(), 1051,
            LAT, LON, reporter.id().value(), null));
    }

    private AlertStatus status(UUID alertId) {
        return AlertStatus.valueOf(jdbc.queryForObject(
            "SELECT status FROM alerts WHERE id = ?", String.class, alertId));
    }

    private int dispatchesFor(UUID alertId) {
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM signal_dispatches WHERE alert_id = ?", Integer.class, alertId);
        return count == null ? 0 : count;
    }

    private int outstandingDispatches() {
        Integer count = jdbc.queryForObject(
            "SELECT count(*) FROM event_publication WHERE completion_date IS NULL AND " + DISPATCHER,
            Integer.class);
        return count == null ? 0 : count;
    }

    private void await(BooleanSupplier condition) {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                TimeUnit.MILLISECONDS.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        throw new AssertionError("condition never held; outstanding dispatches: " + outstandingDispatches());
    }
}
