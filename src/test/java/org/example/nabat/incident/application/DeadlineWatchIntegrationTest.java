package org.example.nabat.incident.application;

import org.example.nabat.PostgresTestSupport;
import org.example.nabat.identity.adapter.out.persistence.UserJpaEntity;
import org.example.nabat.identity.adapter.out.persistence.UserJpaRepository;
import org.example.nabat.identity.application.port.out.EmailSender;
import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.adapter.out.persistence.AlertJpaRepository;
import org.example.nabat.incident.application.port.in.WatchDeadlinesUseCase;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.jurisdiction.application.port.out.SignalMailer;
import org.example.nabat.jurisdiction.application.port.out.SignalMailer.SignalMail;
import org.example.nabat.jurisdiction.application.port.out.SignalMailer.SignalMail.Kind;
import org.example.nabat.shared.domain.Location;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * The deadline watch against the real tables and listeners: a report past its deadline gets
 * one reminder to its authority, and — nothing well after that — one mail to the municipality.
 * One each, however often the watch runs.
 */
@SpringBootTest
class DeadlineWatchIntegrationTest extends PostgresTestSupport {

    private static final long WAIT = Duration.ofSeconds(10).toMillis();

    @Autowired private WatchDeadlinesUseCase watch;
    @Autowired private AlertRepository alerts;
    @Autowired private AlertJpaRepository alertRows;
    @Autowired private UserJpaRepository users;
    @Autowired private JdbcTemplate jdbc;

    @MockitoBean private SignalMailer mailer;
    @MockitoBean private EmailSender accountMail;

    private UUID reporter;

    @BeforeEach
    void setUp() {
        jdbc.update("DELETE FROM event_publication");
        jdbc.update("DELETE FROM signal_dispatches");
        alertRows.deleteAllInBatch();
        users.deleteAll();
        Mockito.reset(mailer);
        User user = User.create("reporter@example.com", "hash", "Подател");
        users.saveAndFlush(UserJpaEntity.from(user));
        reporter = user.id().value();
    }

    @Test
    void remindsTheAuthorityOnceThenTellsTheMunicipalityOnce() {
        Alert late = waiting(Instant.now().minus(Duration.ofDays(1)));

        assertThat(watch.watch()).isEqualTo(new WatchDeadlinesUseCase.Result(1, 0));
        ArgumentCaptor<SignalMail> reminder = ArgumentCaptor.forClass(SignalMail.class);
        verify(mailer, timeout(WAIT)).send(reminder.capture());
        assertThat(reminder.getValue().kind()).isEqualTo(Kind.REMINDER);
        assertThat(reminder.getValue().to()).isEqualTo("lozenets@rayon.nabat.test");

        // Again, at once: the reminder was sent, and the escalation is not due yet.
        assertThat(watch.watch()).isEqualTo(new WatchDeadlinesUseCase.Result(0, 0));

        // Fifteen days of silence after the reminder.
        jdbc.update("UPDATE report_deadline_steps SET taken_at = taken_at - INTERVAL '15 days' WHERE alert_id = ?",
            late.id().value());
        assertThat(watch.watch()).isEqualTo(new WatchDeadlinesUseCase.Result(0, 1));
        verify(mailer, timeout(WAIT)).send(argThat(m ->
            m.kind() == Kind.ESCALATION && m.to().equals("obshtina@institution.nabat.test")
                && m.note().startsWith("Район Лозенец не е отговорил")));

        assertThat(watch.watch()).isEqualTo(new WatchDeadlinesUseCase.Result(0, 0));
    }

    /** Answered or still in time: nothing to chase. */
    @Test
    void leavesAloneWhatIsInTimeOrAnswered() {
        waiting(Instant.now().plus(Duration.ofDays(5)));
        alerts.save(waiting(Instant.now().minus(Duration.ofDays(3))).acknowledge());

        assertThat(watch.watch()).isEqualTo(new WatchDeadlinesUseCase.Result(0, 0));
    }

    /** A new deadline — after a forward or a reopening — is reminded about in its own right. */
    @Test
    void aNewDeadlineGetsItsOwnReminder() {
        Alert late = waiting(Instant.now().minus(Duration.ofDays(2)));
        watch.watch();

        jdbc.update("UPDATE alerts SET answer_due_at = now() - INTERVAL '1 hour' WHERE id = ?", late.id().value());

        assertThat(watch.watch()).isEqualTo(new WatchDeadlinesUseCase.Result(1, 0));
    }

    private Alert waiting(Instant dueAt) {
        Alert alert = alerts.save(Alert.create("Дупка", "Дълбока", 1007, Location.of(42.6750, 23.3200),
            reporter, null, "LOZENETS", "LOZENETS"));
        return alerts.save(alert.markSent(dueAt));
    }
}
