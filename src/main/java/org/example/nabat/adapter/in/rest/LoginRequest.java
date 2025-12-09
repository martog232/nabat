package org.example.nabat.adapter.in.rest;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import org.example.nabat.application.port.in.LoginUserUseCase;

public record LoginRequest(
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    String email,
    
    @NotBlank(message = "Password is required")
    String password
) {
    public LoginUserUseCase.LoginCommand toCommand() {
        return new LoginUserUseCase.LoginCommand(email, password);
    }
}
