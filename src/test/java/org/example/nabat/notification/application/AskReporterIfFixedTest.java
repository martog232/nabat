package org.example.nabat.notification.application;

import org.example.nabat.identity.domain.UserId;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertResolved;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.ClosedBy;
import org.example.nabat.notification.application.port.in.SendNotificationUseCase;
import org.example.nabat.notification.application.port.in.SendNotificationUseCase.ResolvedNotificationCommand;
import org.example.nabat.shared.domain.Location;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class AskReporterIfFixedTest {

    private final SendNotificationUseCase notifications = mock(SendNotificationUseCase.class);
    private final AskReporterIfFixed listener = new AskReporterIfFixed(notifications);

    private final UUID reporterId = UUID.randomUUID();
    private final AlertId alertId = AlertId.generate();

    @Test
    void asksTheReporterWhenTheDistrictClosedIt() {
        listener.on(new AlertResolved(resolvedBy(ClosedBy.DISTRICT)));

        verify(notifications).sendResolvedNotification(
            new ResolvedNotificationCommand(UserId.of(reporterId), alertId, "Дупка", true));
    }

    @Test
    void asksTheReporterWhenAModeratorClosedIt() {
        listener.on(new AlertResolved(resolvedBy(ClosedBy.MODERATOR)));

        verify(notifications).sendResolvedNotification(
            new ResolvedNotificationCommand(UserId.of(reporterId), alertId, "Дупка", false));
    }

    /** They have already said: closing it was their answer. */
    @Test
    void doesNotAskAReporterWhoClosedItThemselves() {
        listener.on(new AlertResolved(resolvedBy(ClosedBy.REPORTER)));

        verifyNoInteractions(notifications);
    }

    private Alert resolvedBy(ClosedBy by) {
        return new Alert(alertId, "Дупка", "Дълбока", 1007, Location.of(42.67, 23.32), Instant.now(),
            AlertStatus.RESOLVED, reporterId, 0, 0, 0, 0, Instant.now(), null, "LOZENETS", by);
    }
}
