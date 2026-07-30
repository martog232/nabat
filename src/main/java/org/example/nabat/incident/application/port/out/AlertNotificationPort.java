package org.example.nabat.incident.application.port.out;

import org.example.nabat.incident.domain.Alert;

import java.util.List;
import java.util.UUID;

public interface AlertNotificationPort {

    /** Broadcasts a newly-created alert to all subscribed users. */
    void broadcastAlert(Alert alert, List<UUID> userIds);

    /** Broadcasts an alert update (vote counts, resolve) to all connected users. */
    void broadcastAlertUpdate(Alert alert);
}
