package org.example.nabat.notification.application;

import org.example.nabat.identity.domain.UserId;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertResolved;
import org.example.nabat.incident.domain.ClosedBy;
import org.example.nabat.notification.application.port.in.SendNotificationUseCase;
import org.example.nabat.notification.application.port.in.SendNotificationUseCase.ResolvedNotificationCommand;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * When someone else closes a report as resolved, its reporter is the first person to ask
 * whether it really is fixed — they saw the problem, and they know where it was.
 *
 * <p>Not when the reporter closed it themselves: they have already said.
 *
 * <p>After commit, through the Event Publication Registry, like the other listeners on
 * report events. The notification is not idempotent, so a replay after a crash between
 * sending and completing would ask twice; that is accepted for the same reason as the
 * dispatch mail — a question asked twice costs less than one never asked. Do not rename
 * this class or {@link #on} with publications outstanding: the registry finds listeners by
 * those names.
 */
@Component
public class AskReporterIfFixed {

    private final SendNotificationUseCase notifications;

    public AskReporterIfFixed(SendNotificationUseCase notifications) {
        this.notifications = notifications;
    }

    @ApplicationModuleListener
    public void on(AlertResolved event) {
        Alert alert = event.alert();
        if (alert.closedBy() == null || alert.closedBy() == ClosedBy.REPORTER) {
            return;
        }
        notifications.sendResolvedNotification(new ResolvedNotificationCommand(
            UserId.of(alert.reportedBy()),
            alert.id(),
            alert.title(),
            alert.closedBy() == ClosedBy.DISTRICT));
    }
}
