package org.example.nabat.identity.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;

public record ResetPasswordRequest(
    @NotBlank(message = "Token is required")
    String token,

    // Same policy as registration, and by the same annotation. These were two independent
    // @Size(min = 6) declarations, so raising the bar on registration alone would have left
    // password reset as a way around it.
    @NotBlank(message = "New password is required")
    @StrongPassword
    String newPassword
) {}

