package org.example.nabat.identity.application.port.in;

import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;

import java.util.Optional;

/**
 * Decides whether a presented credential still names a usable session.
 *
 * <p>Verifying a signature is not the same as accepting a session: the account may
 * have been disabled or deleted since the token was minted, and a password reset
 * bumps {@code tokenVersion} so that tokens issued before it are stale while still
 * inside their expiry window. Those three checks used to live only in
 * {@code JwtAuthenticationFilter}, so the WebSocket handshake — the one other
 * entry point that authenticates a bearer token itself — accepted revoked tokens.
 * Both now come through here, which is what keeps them from drifting apart again.
 */
public interface AuthenticateSessionUseCase {

    /**
     * Verifies an access token and the account behind it.
     *
     * @return the user, or empty if the token is invalid, of the wrong type, or no
     *         longer names an accepted session
     */
    Optional<User> authenticateAccessToken(String accessToken);

    /**
     * Re-checks an account whose identity was established by some other means — a
     * redeemed WebSocket ticket, which carries no token version of its own.
     *
     * @return the user, or empty if the account no longer exists or is disabled
     */
    Optional<User> resolveActiveUser(UserId userId);
}
