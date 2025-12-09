package org.example.nabat.application.port.in;

import org.example.nabat.domain.model.User;

public interface RegisterUserUseCase {
    User register(RegisterCommand command);

    record RegisterCommand(
        String email,
        String password,
        String displayName
    ) {}
}
