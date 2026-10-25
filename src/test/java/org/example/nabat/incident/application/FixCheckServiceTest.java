package org.example.nabat.incident.application;

import org.example.nabat.identity.domain.Role;
import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.incident.application.port.in.CheckFixUseCase.FixCheckView;
import org.example.nabat.incident.application.port.out.AlertNotificationPort;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.incident.application.port.out.FixCheckStore;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.ClosedBy;
import org.example.nabat.incident.domain.FixCheckTally;
import org.example.nabat.incident.domain.FixVerdict;
import org.example.nabat.shared.domain.Location;
import org.example.nabat.shared.domain.NotAuthorizedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FixCheckServiceTest {

    @Mock private AlertRepository alerts;
    @Mock private FixCheckStore store;
    @Mock private AlertNotificationPort broadcasts;

    private FixCheckService service;

    private final AlertId alertId = AlertId.generate();
    private final UUID reporterId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new FixCheckService(alerts, store, broadcasts);
    }

    @Test
    void recordsAnAnswerAndPutsTheNewTallyOnOpenMaps() {
        when(alerts.findById(alertId)).thenReturn(Optional.of(report(AlertStatus.RESOLVED)));
        FixCheckTally after = new FixCheckTally(0, 0, 1, null);
        when(store.record(eq(alertId.value()), any(), eq(FixVerdict.NOT_FIXED), eq(false), any())).thenReturn(after);

        FixCheckView view = service.check(alertId, citizen(UUID.randomUUID()), FixVerdict.NOT_FIXED);

        assertThat(view.tally()).isEqualTo(after);
        assertThat(view.mine()).isEqualTo(FixVerdict.NOT_FIXED);
        ArgumentCaptor<Alert> broadcast = ArgumentCaptor.forClass(Alert.class);
        verify(broadcasts).broadcastAlertUpdate(broadcast.capture());
        assertThat(broadcast.getValue().fixCheck()).isEqualTo(after);
    }

    @Test
    void marksTheReportersOwnAnswer() {
        when(alerts.findById(alertId)).thenReturn(Optional.of(report(AlertStatus.RESOLVED)));
        when(store.record(any(), any(), any(), anyBoolean(), any())).thenReturn(FixCheckTally.NONE);

        service.check(alertId, citizen(reporterId), FixVerdict.FIXED);

        verify(store).record(eq(alertId.value()), eq(reporterId), eq(FixVerdict.FIXED), eq(true), any(Instant.class));
    }

    /** Nothing to check on an open report, and a rejected one was never claimed fixed. */
    @Test
    void refusesAReportThatIsNotResolved() {
        when(alerts.findById(alertId)).thenReturn(Optional.of(report(AlertStatus.IN_PROGRESS)));

        assertThatThrownBy(() -> service.check(alertId, citizen(UUID.randomUUID()), FixVerdict.FIXED))
            .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(store, broadcasts);
    }

    @Test
    void theReportsOwnDistrictCannotCheckItsOwnWork() {
        when(alerts.findById(alertId)).thenReturn(Optional.of(report(AlertStatus.RESOLVED)));

        assertThatThrownBy(() -> service.check(alertId, official("LOZENETS"), FixVerdict.FIXED))
            .isInstanceOf(NotAuthorizedException.class);
        verifyNoInteractions(store, broadcasts);
    }

    @Test
    void anotherDistrictsOfficialAnswersLikeAnyoneElse() {
        when(alerts.findById(alertId)).thenReturn(Optional.of(report(AlertStatus.RESOLVED)));
        when(store.record(any(), any(), any(), anyBoolean(), any())).thenReturn(FixCheckTally.NONE);

        service.check(alertId, official("MLADOST"), FixVerdict.PARTLY);

        verify(store).record(any(), any(), eq(FixVerdict.PARTLY), eq(false), any());
    }

    @Test
    void showsTheTallyOnTheReportAndTheCallersOwnAnswer() {
        FixCheckTally tally = new FixCheckTally(2, 0, 0, FixVerdict.FIXED);
        when(alerts.findById(alertId)).thenReturn(Optional.of(report(AlertStatus.RESOLVED).withFixCheck(tally)));
        User me = citizen(UUID.randomUUID());
        when(store.verdictOf(alertId.value(), me.id().value())).thenReturn(Optional.of(FixVerdict.FIXED));

        FixCheckView view = service.view(alertId, me);

        assertThat(view.tally()).isEqualTo(tally);
        assertThat(view.mine()).isEqualTo(FixVerdict.FIXED);
    }

    private Alert report(AlertStatus status) {
        return new Alert(alertId, "Дупка", "Дълбока", 1007, Location.of(42.67, 23.32), Instant.now(), status,
            reporterId, 0, 0, 0, 0, status == AlertStatus.RESOLVED ? Instant.now() : null, null, "LOZENETS",
            status == AlertStatus.RESOLVED ? ClosedBy.AUTHORITY : null);
    }

    private static User citizen(UUID id) {
        return new User(UserId.of(id), "c@example.com", "p", "Гражданин", Role.USER, true, true,
            Instant.now(), Instant.now(), 0, null);
    }

    private static User official(String district) {
        return new User(UserId.of(UUID.randomUUID()), "o@example.com", "p", "Служител", Role.OFFICIAL, true, true,
            Instant.now(), Instant.now(), 0, district);
    }
}
