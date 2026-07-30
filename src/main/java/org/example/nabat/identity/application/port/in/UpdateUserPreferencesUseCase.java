package org.example.nabat.identity.application.port.in;

import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;

public interface UpdateUserPreferencesUseCase {
    User updatePreferences(UpdatePreferencesCommand command);

    record UpdatePreferencesCommand(
        UserId userId,
        int notificationRadiusKm,
        Double lastKnownLat,
        Double lastKnownLng
    ) {}
}
