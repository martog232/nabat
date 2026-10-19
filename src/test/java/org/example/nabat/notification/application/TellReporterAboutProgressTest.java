package org.example.nabat.notification.application;

import org.example.nabat.identity.domain.UserId;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.AnswerOverdue;
import org.example.nabat.incident.domain.ClosedBy;
import org.example.nabat.incident.domain.RejectionDecided;
import org.example.nabat.incident.domain.ReportProgressed;
import org.example.nabat.incident.domain.ReportRejection;
import org.example.nabat.notification.application.port.in.SendNotificationUseCase;
import org.example.nabat.notification.application.port.in.SendNotificationUseCase.Progress;
import org.example.nabat.notification.application.port.in.SendNotificationUseCase.ProgressNotificationCommand;
import org.example.nabat.shared.domain.Location;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class TellReporterAboutProgressTest {

    private final SendNotificationUseCase notifications = mock(SendNotificationUseCase.class);
    private final TellReporterAboutProgress listener = new TellReporterAboutProgress(notifications);

    private final UUID reporterId = UUID.randomUUID();
    private final AlertId alertId = AlertId.generate();

    @Test
    void tellsTheReporterTheAuthorityTookTheReport() {
        listener.on(new ReportProgressed(report(AlertStatus.ACKNOWLEDGED, null), "Район Лозенец"));

        verify(notifications).sendProgressNotification(new ProgressNotificationCommand(
            UserId.of(reporterId), alertId, "Дупка", Progress.ACKNOWLEDGED, "Район Лозенец", false, null));
    }

    @Test
    void tellsTheReporterWorkStarted() {
        listener.on(new ReportProgressed(report(AlertStatus.IN_PROGRESS, null), "Район Лозенец"));

        verify(notifications).sendProgressNotification(new ProgressNotificationCommand(
            UserId.of(reporterId), alertId, "Дупка", Progress.IN_PROGRESS, "Район Лозенец", false, null));
    }

    /** A moderator's rejection is not the authority's word: the message says whose it was, and why. */
    @Test
    void saysWhoRejectedItAndWhy() {
        listener.on(new ReportProgressed(report(AlertStatus.REJECTED, ClosedBy.AUTHORITY), "Район Лозенец", "Дублира"));
        listener.on(new ReportProgressed(report(AlertStatus.REJECTED, ClosedBy.MODERATOR), "Район Лозенец", "Дублира"));

        verify(notifications).sendProgressNotification(new ProgressNotificationCommand(
            UserId.of(reporterId), alertId, "Дупка", Progress.REJECTED, "Район Лозенец", false, "Дублира"));
        verify(notifications).sendProgressNotification(new ProgressNotificationCommand(
            UserId.of(reporterId), alertId, "Дупка", Progress.REJECTED, "Район Лозенец", true, "Дублира"));
    }

    /** The answer to their dispute: the report is open again with its authority, or the rejection stands. */
    @Test
    void tellsTheReporterWhatCameOfTheirDispute() {
        listener.on(new RejectionDecided(report(AlertStatus.SENT, null), ReportRejection.Decision.REOPENED, "Район Лозенец"));
        listener.on(new RejectionDecided(report(AlertStatus.REJECTED, ClosedBy.AUTHORITY), ReportRejection.Decision.UPHELD, "Район Лозенец"));

        verify(notifications).sendProgressNotification(new ProgressNotificationCommand(
            UserId.of(reporterId), alertId, "Дупка", Progress.REOPENED, "Район Лозенец", false, null));
        verify(notifications).sendProgressNotification(new ProgressNotificationCommand(
            UserId.of(reporterId), alertId, "Дупка", Progress.REJECTION_UPHELD, "Район Лозенец", false, null));
    }

    /** Silence is news too: the reminder, and later the escalation. */
    @Test
    void tellsTheReporterTheAuthorityWasChased() {
        Alert waiting = report(AlertStatus.SENT, null);
        listener.on(new AnswerOverdue(waiting, AnswerOverdue.Stage.REMINDER, Instant.now(), "Район Лозенец"));
        listener.on(new AnswerOverdue(waiting, AnswerOverdue.Stage.ESCALATION, Instant.now(), "Район Лозенец"));

        verify(notifications).sendProgressNotification(new ProgressNotificationCommand(
            UserId.of(reporterId), alertId, "Дупка", Progress.OVERDUE_REMINDED, "Район Лозенец", false, null));
        verify(notifications).sendProgressNotification(new ProgressNotificationCommand(
            UserId.of(reporterId), alertId, "Дупка", Progress.ESCALATED, "Район Лозенец", false, null));
    }

    /** Resolving has its own notification, which also asks whether it is really fixed. */
    @Test
    void saysNothingForAnyOtherStatus() {
        listener.on(new ReportProgressed(report(AlertStatus.RESOLVED, ClosedBy.AUTHORITY), "Район Лозенец"));
        listener.on(new ReportProgressed(report(AlertStatus.SENT, null), "Район Лозенец"));

        verifyNoInteractions(notifications);
    }

    private Alert report(AlertStatus status, ClosedBy closedBy) {
        return new Alert(alertId, "Дупка", "Дълбока", 1007, Location.of(42.67, 23.32), Instant.now(),
            status, reporterId, 0, 0, 0, 0, null, null, "LOZENETS", closedBy);
    }
}
