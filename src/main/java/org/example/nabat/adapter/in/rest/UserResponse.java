package org.example.nabat.adapter.in.rest;

import org.example.nabat.domain.model.Role;
import org.example.nabat.domain.model.User;

import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    String displayName,
    Role role
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
