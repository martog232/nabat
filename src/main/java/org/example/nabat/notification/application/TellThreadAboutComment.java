package org.example.nabat.notification.application;

import org.example.nabat.identity.domain.UserId;
import org.example.nabat.incident.domain.CommentAdded;
import org.example.nabat.notification.application.port.in.SendNotificationUseCase;
import org.example.nabat.notification.application.port.in.SendNotificationUseCase.CommentNotificationCommand;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Someone wrote in a report's thread: its reporter and everyone else who has written there
 * hear of it. Who that is comes on the event — incident works it out, where the thread is.
 *
 * <p>After commit, through the Event Publication Registry, like {@link AskReporterIfFixed}.
 * The listener runs in a transaction of its own, so the notifications are stored together or
 * not at all; but each is pushed to its recipient's socket as it is made, so a failure part
 * way through, and the replay after it, can show some people the same one twice. Accepted for
 * the same reason as there. Do not rename this class or {@link #on} with publications
 * outstanding: the registry finds listeners by those names.
 */
@Component
public class TellThreadAboutComment {

    private final SendNotificationUseCase notifications;

    public TellThreadAboutComment(SendNotificationUseCase notifications) {
        this.notifications = notifications;
    }

    @ApplicationModuleListener
    public void on(CommentAdded event) {
        for (var recipient : event.recipients()) {
            notifications.sendCommentNotification(new CommentNotificationCommand(
                UserId.of(recipient),
                event.alertId(),
                event.alertTitle(),
                UserId.of(event.authorId()),
                event.authorName(),
                event.byDistrict()));
        }
    }
}
