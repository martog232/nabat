package org.example.nabat.notification.application;

import org.example.nabat.identity.domain.UserId;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.ClosedBy;
import org.example.nabat.incident.domain.ReportProgressed;
import org.example.nabat.notification.application.port.in.SendNotificationUseCase;
import org.example.nabat.notification.application.port.in.SendNotificationUseCase.Progress;
import org.example.nabat.notification.application.port.in.SendNotificationUseCase.ProgressNotificationCommand;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * The authority took the report, started on it, or turned it down: its reporter hears. These
 * are the moves that say someone is dealing with it — or that nobody will — and without them
 * a reporter learns nothing between "sent" and "resolved".
 *
 * <p>After commit, through the Event Publication Registry, like {@link AskReporterIfFixed},
 * and accepted to repeat on a replay for the same reason. Do not rename this class or
 * {@link #on} with publications outstanding: the registry finds listeners by those names.
 */
@Component
public class TellReporterAboutProgress {

    private final SendNotificationUseCase notifications;

    public TellReporterAboutProgress(SendNotificationUseCase notifications) {
        this.notifications = notifications;
    }

    @ApplicationModuleListener
    public void on(ReportProgressed event) {
        Alert alert = event.alert();
        Progress progress = switch (alert.status()) {
            case ACKNOWLEDGED -> Progress.ACKNOWLEDGED;
            case IN_PROGRESS -> Progress.IN_PROGRESS;
            case REJECTED -> Progress.REJECTED;
            // Not published for any other status; a replay of something else says nothing.
            default -> null;
        };
        if (progress == null) {
            return;
        }
        notifications.sendProgressNotification(new ProgressNotificationCommand(
            UserId.of(alert.reportedBy()),
            alert.id(),
            alert.title(),
            progress,
            event.authorityName(),
            alert.closedBy() == ClosedBy.MODERATOR));
    }
}
