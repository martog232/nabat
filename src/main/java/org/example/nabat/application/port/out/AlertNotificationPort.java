package org.example.nabat.application.port.out;

import org.example.nabat.domain.model.Alert;

import java.util.List;
import java.util.UUID;

public interface AlertNotificationPort {

    void broadcastAlert(Alert alert, List<UUID> userIds);

    void notifyUser(UUID userId, Alert alert);
}
