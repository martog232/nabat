package org.example.nabat.incident.application;

import org.example.nabat.incident.application.port.out.AlertNotificationPort;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertCreated;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.shared.domain.Location;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * The push half of alert creation, which used to live inside
 * {@code CreateAlertService.createAlert} and inside its transaction.
 */
@ExtendWith(MockitoExtension.class)
class NewAlertFanoutTest {

    @Mock
    private AlertNotificationPort notificationPort;

    /**
     * To everyone, and only as a new report. There is no audience to work out any more:
     * who is near a pothole is not who should hear about it, and the map is for whoever
     * has it open — including the reporter, whose own client upserts by id.
     */
    @Test
    void putsEveryNewReportOnEveryOpenMap() {
        Alert alert = new Alert(
            AlertId.generate(),
            "Pothole",
            "Outside number 12",
            1007,
            Location.of(42.69, 23.32),
            Instant.now(),
            AlertStatus.SUBMITTED,
            UUID.randomUUID(),
            0, 0, 0, 0,
            null,
            null, "LOZENETS", null
        );

        new NewAlertFanout(notificationPort).on(new AlertCreated(alert));

        verify(notificationPort).broadcastNewAlert(alert);
        verifyNoMoreInteractions(notificationPort);
    }
}
