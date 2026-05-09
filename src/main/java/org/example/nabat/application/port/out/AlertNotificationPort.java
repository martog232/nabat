package org.example.nabat.application.port.out;

import org.example.nabat.domain.model.Alert;

import java.util.List;
import java.util.UUID;

public interface AlertNotificationPort {

    /** Broadcasts a newly-created alert to all subscribed users. */
    void broadcastAlert(Alert alert, List<UUID> userIds);
}
