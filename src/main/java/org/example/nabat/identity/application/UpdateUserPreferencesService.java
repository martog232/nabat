package org.example.nabat.identity.application;

import org.example.nabat.shared.UseCase;
import org.example.nabat.identity.application.port.in.UpdateUserPreferencesUseCase;
import org.example.nabat.identity.application.port.out.UserRepository;
import org.example.nabat.identity.domain.UserNotFoundException;
import org.example.nabat.identity.domain.NotificationRadius;
import org.example.nabat.identity.domain.User;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class UpdateUserPreferencesService implements UpdateUserPreferencesUseCase {

    private final UserRepository userRepository;

    public UpdateUserPreferencesService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public User updatePreferences(UpdatePreferencesCommand command) {
        // Validated against the same allow-list the users table CHECK constraint uses,
        // so an out-of-range value fails as a 400 rather than a constraint violation.
        NotificationRadius.requireSupported(command.notificationRadiusKm());

        User user = userRepository.findById(command.userId())
            .orElseThrow(() -> new UserNotFoundException(command.userId()));

        boolean hasLocation = command.lastKnownLat() != null && command.lastKnownLng() != null;
        User updatedUser = hasLocation
            ? user.withLocation(command.lastKnownLat(), command.lastKnownLng(), command.notificationRadiusKm())
            // Was a hand-inlined 13-argument copy of the User record — the seventh such
            // copy in the codebase. User now exposes this directly.
            : user.withNotificationRadius(command.notificationRadiusKm());

        return userRepository.save(updatedUser);
    }
}
