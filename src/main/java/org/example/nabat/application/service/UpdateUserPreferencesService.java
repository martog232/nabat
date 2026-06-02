package org.example.nabat.application.service;

import org.example.nabat.application.UseCase;
import org.example.nabat.application.port.in.UpdateUserPreferencesUseCase;
import org.example.nabat.application.port.out.UserRepository;
import org.example.nabat.domain.model.User;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@UseCase
public class UpdateUserPreferencesService implements UpdateUserPreferencesUseCase {

    private static final Set<Integer> ALLOWED_RADII = Set.of(1, 5, 10, 25, 50);

    private final UserRepository userRepository;

    public UpdateUserPreferencesService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional
    public User updatePreferences(UpdatePreferencesCommand command) {
        validateRadius(command.notificationRadiusKm());

        User user = userRepository.findById(command.userId())
            .orElseThrow(() -> new UsernameNotFoundException("User not found: " + command.userId().value()));

        User updatedUser = command.lastKnownLat() != null && command.lastKnownLng() != null
            ? user.withLocation(command.lastKnownLat(), command.lastKnownLng(), command.notificationRadiusKm())
            : withNotificationRadius(user, command.notificationRadiusKm());

        return userRepository.save(updatedUser);
    }

    private void validateRadius(int notificationRadiusKm) {
        if (!ALLOWED_RADII.contains(notificationRadiusKm)) {
            throw new IllegalArgumentException("Unsupported notification radius: " + notificationRadiusKm);
        }
    }

    private User withNotificationRadius(User user, int notificationRadiusKm) {
        return new User(
            user.id(),
            user.email(),
            user.password(),
            user.displayName(),
            user.role(),
            user.enabled(),
            user.emailVerified(),
            user.createdAt(),
            Instant.now(),
            notificationRadiusKm,
            user.lastKnownLat(),
            user.lastKnownLng(),
            user.locationUpdatedAt()
        );
    }
}
