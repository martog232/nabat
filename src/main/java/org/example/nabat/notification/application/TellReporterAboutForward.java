package org.example.nabat.notification.application;

import org.example.nabat.identity.domain.UserId;
import org.example.nabat.incident.domain.ReportForwarded;
import org.example.nabat.notification.application.port.in.SendNotificationUseCase;
import org.example.nabat.notification.application.port.in.SendNotificationUseCase.ForwardedNotificationCommand;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * A report went to another authority: its reporter hears where, and why. They filed it with
 * one authority in mind and would otherwise find a different name on it with no explanation.
 *
 * <p>After commit, through the Event Publication Registry, like {@link AskReporterIfFixed},
 * and accepted to repeat on a replay for the same reason. Do not rename this class or
 * {@link #on} with publications outstanding: the registry finds listeners by those names.
 */
@Component
public class TellReporterAboutForward {

    private final SendNotificationUseCase notifications;

    public TellReporterAboutForward(SendNotificationUseCase notifications) {
        this.notifications = notifications;
    }

    @ApplicationModuleListener
    public void on(ReportForwarded event) {
        // A moderator who reported it and then forwarded it knows already.
        if (event.alert().reportedBy().equals(event.forward().forwardedBy())) {
            return;
        }
        notifications.sendForwardedNotification(new ForwardedNotificationCommand(
            UserId.of(event.alert().reportedBy()),
            event.alert().id(),
            event.alert().title(),
            event.toAuthorityName(),
            event.forward().reason()));
    }
}
