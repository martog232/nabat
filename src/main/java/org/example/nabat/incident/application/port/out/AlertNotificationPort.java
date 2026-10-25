package org.example.nabat.incident.application.port.out;

import org.example.nabat.incident.domain.Alert;

public interface AlertNotificationPort {

    /** Puts a newly-created report on every connected client's map. */
    void broadcastNewAlert(Alert alert);

    /** Broadcasts an alert update (vote counts, status) to all connected users. */
    void broadcastAlertUpdate(Alert alert);
}
