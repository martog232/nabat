package org.example.nabat.application.port.in;

public interface RefreshTokenUseCase {
    AuthTokens refresh(String refreshToken);

    record AuthTokens(
        String accessToken,
        String refreshToken,
        long expiresIn
    ) {}
}
