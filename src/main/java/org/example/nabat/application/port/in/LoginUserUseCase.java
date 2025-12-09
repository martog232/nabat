package org.example.nabat.application.port.in;

import org.example.nabat.domain.model.User;

public interface LoginUserUseCase {
    LoginResult login(LoginCommand command);

    record LoginCommand(
        String email,
        String password
    ) {}

    record LoginResult(
        String accessToken,
        String refreshToken,
        long expiresIn,
        User user
    ) {}
}
