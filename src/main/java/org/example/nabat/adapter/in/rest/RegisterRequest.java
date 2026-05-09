package org.example.nabat.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.example.nabat.application.port.in.RegisterUserUseCase;

@Schema(description = "Request body for creating a new user account")
public record RegisterRequest(
    @Schema(description = "E-mail address (used as login)", example = "alice@example.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    String email,

    @Schema(description = "Password — minimum 6 characters", example = "s3cr3tP@ss", minLength = 6)
    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    String password,

    @Schema(description = "Public display name shown to other users", example = "Alice", minLength = 2, maxLength = 50)
    @NotBlank(message = "Display name is required")
    @Size(min = 2, max = 50, message = "Display name must be between 2 and 50 characters")
    String displayName
) {
    public RegisterUserUseCase.RegisterCommand toCommand() {
        return new RegisterUserUseCase.RegisterCommand(email, password, displayName);
    }
}
