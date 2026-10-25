package org.example.nabat.incident.application.port.in;

import org.example.nabat.incident.domain.Alert;

import java.util.UUID;

public interface CreateAlertUseCase {

    Alert createAlert(CreateAlertCommand command);

    record CreateAlertCommand(
        String title,
        String description,
        /** A subcategory code from the category tree — never a group. */
        int categoryCode,
        double latitude,
        double longitude,
        UUID reportedBy,
        String photoUrl
    ) {
    }
}
