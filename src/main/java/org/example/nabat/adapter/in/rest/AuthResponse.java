package org.example.nabat.adapter.in.rest;

import org.example.nabat.application.port.in.LoginUserUseCase;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    long expiresIn,
    UserResponse user
) {
    public static AuthResponse from(LoginUserUseCase.LoginResult result) {
        return new AuthResponse(
            result.accessToken(),
            result.refreshToken(),
            result.expiresIn(),
            UserResponse.from(result.user())
        );
    }
}
