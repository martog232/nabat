package org.example.nabat.incident.application.port.in;

import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertSeverity;
import org.example.nabat.incident.domain.AlertType;

import java.util.UUID;

public interface CreateAlertUseCase {

    Alert createAlert(CreateAlertCommand command);

    record CreateAlertCommand(
        String title,
        String description,
        AlertType type,
        AlertSeverity severity,
        double latitude,
        double longitude,
        UUID reportedBy,
        String photoUrl
    ) {
    }
}
