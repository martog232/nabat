package org.example.nabat.application.service;

import org.example.nabat.application.port.in.UpdateUserPreferencesUseCase;
import org.example.nabat.application.port.out.UserRepository;
import org.example.nabat.domain.model.Role;
import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateUserPreferencesServiceTest {

    @Mock
    private UserRepository userRepository;

    private UpdateUserPreferencesService service;

    @BeforeEach
    void setUp() {
        service = new UpdateUserPreferencesService(userRepository);
    }

    @Test
    void updatePreferencesUpdatesLocationAndRadius() {
        User existingUser = user(5, 42.1, 23.1, Instant.parse("2024-01-01T10:00:00Z"));
        when(userRepository.findById(existingUser.id())).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        User updatedUser = service.updatePreferences(new UpdateUserPreferencesUseCase.UpdatePreferencesCommand(
            existingUser.id(),
            10,
            42.3601,
            -71.0589
        ));

        assertEquals(10, updatedUser.notificationRadiusKm());
        assertEquals(42.3601, updatedUser.lastKnownLat());
        assertEquals(-71.0589, updatedUser.lastKnownLng());
        assertNotNull(updatedUser.locationUpdatedAt());
    }

    @Test
    void updatePreferencesRejectsInvalidRadius() {
        assertThrows(IllegalArgumentException.class, () -> service.updatePreferences(
            new UpdateUserPreferencesUseCase.UpdatePreferencesCommand(UserId.generate(), 3, null, null)
        ));
    }

    @Test
    void updatePreferencesKeepsExistingLocationWhenCoordinatesMissing() {
        Instant originalLocationUpdatedAt = Instant.parse("2024-01-01T10:00:00Z");
        User existingUser = user(5, 42.1, 23.1, originalLocationUpdatedAt);
        when(userRepository.findById(existingUser.id())).thenReturn(Optional.of(existingUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.updatePreferences(new UpdateUserPreferencesUseCase.UpdatePreferencesCommand(
            existingUser.id(),
            25,
            null,
            null
        ));

        ArgumentCaptor<User> savedUserCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUserCaptor.capture());
        User savedUser = savedUserCaptor.getValue();
        assertEquals(25, savedUser.notificationRadiusKm());
        assertEquals(existingUser.lastKnownLat(), savedUser.lastKnownLat());
        assertEquals(existingUser.lastKnownLng(), savedUser.lastKnownLng());
        assertEquals(originalLocationUpdatedAt, savedUser.locationUpdatedAt());
    }

    @Test
    void updatePreferencesThrowsWhenUserMissing() {
        UserId userId = UserId.of(UUID.randomUUID());
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThrows(UsernameNotFoundException.class, () -> service.updatePreferences(
            new UpdateUserPreferencesUseCase.UpdatePreferencesCommand(userId, 5, null, null)
        ));
    }

    private User user(int radiusKm, Double lat, Double lng, Instant locationUpdatedAt) {
        Instant now = Instant.now();
        return new User(
            UserId.generate(),
            "prefs@example.com",
            "hash",
            "Prefs User",
            Role.USER,
            true,
            true,
            now.minusSeconds(60),
            now,
            radiusKm,
            lat,
            lng,
            locationUpdatedAt
        );
    }
}
