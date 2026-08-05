package org.example.nabat.identity.application.port.in;

import org.example.nabat.identity.domain.User;

public interface RegisterUserUseCase {

    /**
     * Creates the account and issues its first session.
     *
     * <p>Returning the tokens means the controller no longer has to call
     * {@code login()} with the plaintext password to "auto-login" the new user.
     */
    RegistrationResult register(RegisterCommand command);

    record RegisterCommand(
        String email,
        String password,
        String displayName
    ) {}

    record RegistrationResult(
        User user,
        String accessToken,
        String refreshToken,
        long expiresIn
    ) {}
}
