package org.example.nabat.notification.application;

import org.example.nabat.identity.domain.UserId;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.CommentAdded;
import org.example.nabat.notification.application.port.in.SendNotificationUseCase;
import org.example.nabat.notification.application.port.in.SendNotificationUseCase.CommentNotificationCommand;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class TellThreadAboutCommentTest {

    private final SendNotificationUseCase notifications = mock(SendNotificationUseCase.class);
    private final TellThreadAboutComment listener = new TellThreadAboutComment(notifications);

    @Test
    void tellsEachPersonOnTheEvent() {
        AlertId alertId = AlertId.generate();
        UUID author = UUID.randomUUID();
        UUID reporter = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        listener.on(new CommentAdded(alertId, "Дупка", author, "Служител", true, Set.of(reporter, other)));

        verify(notifications).sendCommentNotification(
            new CommentNotificationCommand(UserId.of(reporter), alertId, "Дупка", UserId.of(author), "Служител", true));
        verify(notifications).sendCommentNotification(
            new CommentNotificationCommand(UserId.of(other), alertId, "Дупка", UserId.of(author), "Служител", true));
        verifyNoMoreInteractions(notifications);
    }
}
