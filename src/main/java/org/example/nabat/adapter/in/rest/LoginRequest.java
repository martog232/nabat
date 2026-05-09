package org.example.nabat.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.example.nabat.application.port.in.LoginUserUseCase;

@Schema(description = "Credentials for logging in")
public record LoginRequest(
    @Schema(description = "Registered e-mail address", example = "alice@example.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    String email,

    @Schema(description = "Account password", example = "s3cr3tP@ss")
    @NotBlank(message = "Password is required")
    String password
) {
    public LoginUserUseCase.LoginCommand toCommand() {
        return new LoginUserUseCase.LoginCommand(email, password);
    }
}
