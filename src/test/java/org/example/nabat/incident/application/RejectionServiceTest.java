package org.example.nabat.incident.application;

import org.example.nabat.identity.domain.Role;
import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.incident.application.port.out.AlertNotificationPort;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.incident.application.port.out.CommentStore;
import org.example.nabat.incident.application.port.out.RejectionStore;
import org.example.nabat.incident.application.port.out.ResponsibleAuthorityPort;
import org.example.nabat.incident.application.port.out.ReportHistory;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.ClosedBy;
import org.example.nabat.incident.domain.Comment;
import org.example.nabat.incident.domain.RejectionDecided;
import org.example.nabat.incident.domain.ReportProgressed;
import org.example.nabat.incident.domain.ReportRejection;
import org.example.nabat.incident.domain.ReportRejection.Decision;
import org.example.nabat.shared.domain.Location;
import org.example.nabat.shared.domain.NotAuthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RejectionServiceTest {

    @Mock private AlertRepository alerts;
    @Mock private RejectionStore rejections;
    @Mock private CommentStore comments;
    @Mock private ResponsibleAuthorityPort authorities;
    @Mock private AlertNotificationPort broadcast;
    @Mock private ApplicationEventPublisher events;
    @Mock private ReportHistory history;

    private RejectionService service;

    private final AlertId alertId = AlertId.generate();
    private final UUID reporterId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new RejectionService(alerts, rejections, comments, authorities, broadcast, events,
            new AnswerDeadline(java.time.Duration.ofDays(30)), history);
        lenient().when(authorities.nameOf("LOZENETS")).thenReturn(Optional.of("Район Лозенец"));
        lenient().when(alerts.save(any(Alert.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void theAuthorityRejectsSayingWhyAndTheReporterHearsIt() {
        given(AlertStatus.SENT);

        Alert rejected = service.reject(alertId, official("LOZENETS"), "  Дублира сигнал от вчера ");

        assertThat(rejected.status()).isEqualTo(AlertStatus.REJECTED);
        assertThat(rejected.closedBy()).isEqualTo(ClosedBy.AUTHORITY);
        ArgumentCaptor<ReportRejection> rejection = ArgumentCaptor.forClass(ReportRejection.class);
        verify(rejections).insert(rejection.capture());
        assertThat(rejection.getValue().reason()).isEqualTo("Дублира сигнал от вчера");
        assertThat(rejection.getValue().authorityCode()).isEqualTo("LOZENETS");
        ArgumentCaptor<Comment> line = ArgumentCaptor.forClass(Comment.class);
        verify(comments).insert(line.capture());
        assertThat(line.getValue().body()).isEqualTo("Отхвърлен: Дублира сигнал от вчера");
        assertThat(line.getValue().byAuthority()).isTrue();
        ArgumentCaptor<ReportProgressed> event = ArgumentCaptor.forClass(ReportProgressed.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().reason()).isEqualTo("Дублира сигнал от вчера");
    }

    /** The strongest move there is says why, like the far milder forward does. */
    @Test
    void aRejectionWithoutAReasonIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> service.reject(alertId, official("LOZENETS"), " "));
        verifyNoInteractions(alerts, rejections, events);
    }

    /** "Not a real problem" is not the reporter's call about their own report. */
    @Test
    void onlyTheAuthorityOrAModeratorRejects() {
        given(AlertStatus.SENT);

        assertThrows(NotAuthorizedException.class, () -> service.reject(alertId, user(reporterId, Role.USER), "Сгреших"));
        assertThrows(NotAuthorizedException.class, () -> service.reject(alertId, official("TRIADITSA"), "Не е наш"));
        assertThat(service.reject(alertId, user(UUID.randomUUID(), Role.MODERATOR), "Спам").closedBy())
            .isEqualTo(ClosedBy.MODERATOR);
    }

    @Test
    void theReporterDisputesOnceAndItGoesInTheThread() {
        given(AlertStatus.REJECTED);
        when(rejections.holdLatest(alertId.value())).thenReturn(Optional.of(rejection()));

        ReportRejection disputed = service.dispute(alertId, user(reporterId, Role.USER), "Дупката още е там");

        assertThat(disputed.disputeReason()).isEqualTo("Дупката още е там");
        verify(rejections).update(disputed);
        ArgumentCaptor<Comment> line = ArgumentCaptor.forClass(Comment.class);
        verify(comments).insert(line.capture());
        assertThat(line.getValue().body()).isEqualTo("Оспорвам отхвърлянето: Дупката още е там");
        assertThat(line.getValue().byReporter()).isTrue();

        when(rejections.holdLatest(alertId.value())).thenReturn(Optional.of(disputed));
        assertThrows(IllegalStateException.class,
            () -> service.dispute(alertId, user(reporterId, Role.USER), "Пак"));
    }

    @Test
    void nobodyButTheReporterDisputesAndOnlyARejectedReport() {
        given(AlertStatus.REJECTED);
        assertThrows(NotAuthorizedException.class,
            () -> service.dispute(alertId, user(UUID.randomUUID(), Role.USER), "Не съм съгласен"));

        given(AlertStatus.SENT);
        assertThrows(IllegalStateException.class,
            () -> service.dispute(alertId, user(reporterId, Role.USER), "Не съм съгласен"));
    }

    /** Rejected before V31: no reason on record, so nothing to dispute. */
    @Test
    void aRejectionWithoutARecordCannotBeDisputed() {
        given(AlertStatus.REJECTED);
        when(rejections.holdLatest(alertId.value())).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
            () -> service.dispute(alertId, user(reporterId, Role.USER), "Не съм съгласен"));
    }

    @Test
    void aModeratorReopensADisputedRejectionWithTheSameAuthority() {
        given(AlertStatus.REJECTED);
        when(rejections.holdLatest(alertId.value()))
            .thenReturn(Optional.of(rejection().dispute("Още е там", Instant.now())));

        Alert reopened = service.decide(alertId, user(UUID.randomUUID(), Role.MODERATOR), Decision.REOPENED);

        assertThat(reopened.status()).isEqualTo(AlertStatus.SENT);
        assertThat(reopened.authorityCode()).isEqualTo("LOZENETS");
        assertThat(reopened.closedBy()).isNull();
        // Back with a full deadline, as if just delivered.
        assertThat(reopened.answerDueAt()).isAfter(Instant.now().plus(java.time.Duration.ofDays(29)));
        ArgumentCaptor<RejectionDecided> event = ArgumentCaptor.forClass(RejectionDecided.class);
        verify(events).publishEvent(event.capture());
        assertThat(event.getValue().decision()).isEqualTo(Decision.REOPENED);
    }

    @Test
    void anUpheldRejectionLeavesTheReportAsItIs() {
        given(AlertStatus.REJECTED);
        when(rejections.holdLatest(alertId.value()))
            .thenReturn(Optional.of(rejection().dispute("Още е там", Instant.now())));

        Alert after = service.decide(alertId, user(UUID.randomUUID(), Role.MODERATOR), Decision.UPHELD);

        assertThat(after.status()).isEqualTo(AlertStatus.REJECTED);
        verify(alerts, never()).save(any());
    }

    @Test
    void onlyAModeratorDecidesAndOnlyADisputedRejection() {
        assertThrows(NotAuthorizedException.class,
            () -> service.decide(alertId, official("LOZENETS"), Decision.REOPENED));

        given(AlertStatus.REJECTED);
        when(rejections.holdLatest(alertId.value())).thenReturn(Optional.of(rejection()));
        assertThrows(IllegalStateException.class,
            () -> service.decide(alertId, user(UUID.randomUUID(), Role.MODERATOR), Decision.REOPENED));
    }

    private void given(AlertStatus status) {
        when(alerts.findById(alertId)).thenReturn(Optional.of(report(status)));
    }

    private Alert report(AlertStatus status) {
        return new Alert(alertId, "Дупка", "Дълбока", 1007, Location.of(42.67, 23.32), Instant.now(), status,
            reporterId, 0, 0, 0, 0, null, null, "LOZENETS",
            status == AlertStatus.REJECTED ? ClosedBy.AUTHORITY : null);
    }

    private ReportRejection rejection() {
        return ReportRejection.of(alertId, UUID.randomUUID(), false, "LOZENETS", "Дублира", Instant.now());
    }

    private static User official(String authority) {
        return new User(UserId.of(UUID.randomUUID()), "o@example.com", "p", "Служител", Role.OFFICIAL, true, true,
            Instant.now(), Instant.now(), 0, authority);
    }

    private static User user(UUID id, Role role) {
        return new User(UserId.of(id), "u@example.com", "p", "Гражданин", role, true, true,
            Instant.now(), Instant.now(), 0, null);
    }
}
