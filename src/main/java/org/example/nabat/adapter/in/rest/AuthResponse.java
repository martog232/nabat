package org.example.nabat.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import org.example.nabat.application.port.in.LoginUserUseCase;

@Schema(description = "Authentication tokens returned after a successful login or token refresh")
public record AuthResponse(
    @Schema(description = "Short-lived JWT access token — include in Authorization: Bearer header") String accessToken,
    @Schema(description = "Long-lived refresh token used to obtain a new access token") String refreshToken,
    @Schema(description = "Access-token validity in seconds", example = "900") long expiresIn,
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
}
