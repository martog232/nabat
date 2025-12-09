package org.example.nabat.application.port.out;

import org.example.nabat.domain.model.User;

public interface TokenProvider {
    String generateAccessToken(User user);
    String generateRefreshToken(User user);
    String getEmailFromToken(String token);
    boolean validateToken(String token);
    boolean isRefreshToken(String token);
    long getJwtExpiration();
}
