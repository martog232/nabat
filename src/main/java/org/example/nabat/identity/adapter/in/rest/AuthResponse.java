package org.example.nabat.identity.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import org.example.nabat.identity.application.port.in.LoginUserUseCase;
import org.example.nabat.identity.application.port.in.RegisterUserUseCase;

@Schema(description = "Authentication tokens returned after a successful login or registration")
public record AuthResponse(
    @Schema(description = "Short-lived JWT access token — include in Authorization: Bearer header") String accessToken,
    @Schema(description = "Single-use refresh token used to obtain a new pair") String refreshToken,
    @Schema(description = "Access-token validity in milliseconds", example = "86400000") long expiresIn,
    @Schema(description = "Basic profile of the authenticated user") UserResponse user
) {
    public static AuthResponse from(LoginUserUseCase.LoginResult result) {
        return new AuthResponse(
            result.accessToken(),
            result.refreshToken(),
            result.expiresIn(),
            UserResponse.from(result.user())
        );
    }

    public static AuthResponse from(RegisterUserUseCase.RegistrationResult result) {
        return new AuthResponse(
            result.accessToken(),
            result.refreshToken(),
            result.expiresIn(),
            UserResponse.from(result.user())
        );
    }
}
