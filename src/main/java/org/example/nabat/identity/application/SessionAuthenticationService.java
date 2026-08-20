package org.example.nabat.identity.application;

import org.example.nabat.identity.application.port.in.AuthenticateSessionUseCase;
import org.example.nabat.identity.application.port.out.TokenProvider;
import org.example.nabat.identity.application.port.out.UserRepository;
import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.shared.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

@UseCase
public class SessionAuthenticationService implements AuthenticateSessionUseCase {

    private static final Logger log = LoggerFactory.getLogger(SessionAuthenticationService.class);

    private final TokenProvider tokenProvider;
    private final UserRepository userRepository;

    public SessionAuthenticationService(TokenProvider tokenProvider, UserRepository userRepository) {
        this.tokenProvider = tokenProvider;
        this.userRepository = userRepository;
    }

    @Override
    public Optional<User> authenticateAccessToken(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return reject("no token presented");
        }

        // One signature verification, which also enforces that this is an access
        // token and that the claims are well formed.
        Optional<TokenProvider.AccessTokenClaims> parsed = tokenProvider.parseAccessToken(accessToken);
        if (parsed.isEmpty()) {
            return reject("invalid or non-access token");
        }
        TokenProvider.AccessTokenClaims claims = parsed.get();

        Optional<User> user = resolveActiveUser(UserId.of(claims.userId()));
        if (user.isEmpty()) {
            return Optional.empty();
        }

        // A password reset or an explicit revocation bumps tokenVersion, which makes
        // every token minted before that point stale even though it is still within
        // its expiry window.
        if (claims.tokenVersion() != user.get().tokenVersion()) {
            return reject("token was invalidated by a credential change");
        }

        return user;
    }

    @Override
    public Optional<User> resolveActiveUser(UserId userId) {
        if (userId == null) {
            return reject("no user id presented");
        }

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            return reject("credential names a user that no longer exists");
        }
        if (!user.enabled()) {
            return reject("user is disabled");
        }
        return Optional.of(user);
    }

    /**
     * Debug, not warn: every branch here is reachable by an unauthenticated caller
     * presenting a malformed token, so logging loudly would let one flood the logs.
     */
    private Optional<User> reject(String reason) {
        log.debug("Rejected credential: {}", reason);
        return Optional.empty();
    }
}
