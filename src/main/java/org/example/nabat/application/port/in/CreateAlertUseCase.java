package org.example.nabat.application.port.in;

import org.example.nabat.domain.model.Alert;
import org.example.nabat.domain.model.AlertSeverity;
import org.example.nabat.domain.model.AlertType;

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
        UUID reportedBy
    ) {
    }
}
