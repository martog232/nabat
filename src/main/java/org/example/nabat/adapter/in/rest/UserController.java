package org.example.nabat.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.example.nabat.application.port.in.UpdateUserPreferencesUseCase;
import org.example.nabat.domain.model.User;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/users")
public class UserController {

    private static final Set<Integer> ALLOWED_RADII = Set.of(1, 5, 10, 25, 50);

    private final UpdateUserPreferencesUseCase updateUserPreferencesUseCase;

    @PatchMapping("/me/preferences")
    public ResponseEntity<UserResponse> updatePreferences(
        @Valid @RequestBody UpdateUserPreferencesRequest request,
        @AuthenticationPrincipal User currentUser
    ) {
        User updatedUser = updateUserPreferencesUseCase.updatePreferences(
            new UpdateUserPreferencesUseCase.UpdatePreferencesCommand(
                currentUser.id(),
                request.notificationRadiusKm(),
                request.lastKnownLat(),
                request.lastKnownLng()
            )
        );
        return ResponseEntity.ok(UserResponse.from(updatedUser));
    }

    @Schema(description = "Request body for updating notification preferences")
    public record UpdateUserPreferencesRequest(
        @Schema(description = "Allowed radius preset in kilometres", example = "10")
        @NotNull Integer notificationRadiusKm,
        @Schema(description = "Latest known latitude", example = "42.3601")
        @DecimalMin(value = "-90.0")
        @DecimalMax(value = "90.0")
        Double lastKnownLat,
        @Schema(description = "Latest known longitude", example = "-71.0589")
        @DecimalMin(value = "-180.0")
        @DecimalMax(value = "180.0")
        Double lastKnownLng
    ) {
        @AssertTrue(message = "notificationRadiusKm must be one of [1, 5, 10, 25, 50]")
        public boolean hasAllowedRadius() {
            return notificationRadiusKm != null && ALLOWED_RADII.contains(notificationRadiusKm);
        }

        @AssertTrue(message = "lastKnownLat and lastKnownLng must both be provided together")
        public boolean hasCompleteLocation() {
            return (lastKnownLat == null) == (lastKnownLng == null);
        }
    }
}
