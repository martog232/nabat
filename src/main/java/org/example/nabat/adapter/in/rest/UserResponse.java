package org.example.nabat.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import org.example.nabat.domain.model.Role;
import org.example.nabat.domain.model.User;

import java.util.UUID;

@Schema(description = "Public profile of a user")
public record UserResponse(
    @Schema(description = "Stable user identifier") UUID id,
    @Schema(description = "E-mail address", example = "alice@example.com") String email,
    @Schema(description = "Display name shown to other users", example = "Alice") String displayName,
    @Schema(description = "Assigned role", example = "USER") Role role
) {
    public static UserResponse from(User user) {
        return new UserResponse(
            user.id().value(),
            user.email(),
            user.displayName(),
            user.role()
        );
    }
}
