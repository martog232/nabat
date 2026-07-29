package org.example.nabat.application.port.out;

import java.time.Duration;

/**
 * Records which refresh tokens have already been exchanged, so that each one can be
 * used exactly once.
 *
 * <p>Refresh tokens were previously not rotated at all: {@code /auth/refresh}
 * handed out a new pair while leaving the presented token valid for its full
 * seven-day lifetime. A stolen refresh token therefore granted indefinite access
 * and there was no way to revoke a single session.
 *
 * <p>Must be shared across instances — a per-instance store would let an attacker
 * replay a token against a different replica.
 */
public interface RefreshTokenStore {

    /**
     * Marks {@code tokenId} as used.
     *
     * @param retainFor how long to remember it — the token's remaining lifetime is
     *                  sufficient, since an expired token fails signature/exp checks anyway
     * @return {@code true} if this was the first use, {@code false} if the token had
     *         already been exchanged (i.e. this is a replay)
     */
    boolean consume(String tokenId, Duration retainFor);
}
