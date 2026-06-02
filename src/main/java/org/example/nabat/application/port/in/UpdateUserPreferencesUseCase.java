package org.example.nabat.application.port.in;

import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.UserId;

public interface UpdateUserPreferencesUseCase {
    User updatePreferences(UpdatePreferencesCommand command);

    record UpdatePreferencesCommand(
        UserId userId,
        int notificationRadiusKm,
        Double lastKnownLat,
        Double lastKnownLng
    ) {}
}
