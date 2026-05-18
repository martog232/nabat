package org.example.nabat.application.port.in;

import org.example.nabat.domain.model.UserId;

public interface VerifyEmailUseCase {

    /** Called from the REST layer after a user registers — creates and emails the token. */
    void sendVerificationEmail(UserId userId);

    /** Called from POST /api/v1/auth/verify — validates the token and marks the user verified. */
    void verifyEmail(String token);
}

