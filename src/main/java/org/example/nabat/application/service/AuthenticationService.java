package org.example.nabat.application.service;

import lombok.extern.slf4j.Slf4j;
import org.example.nabat.application.UseCase;
import org.example.nabat.application.port.in.LoginUserUseCase;
import org.example.nabat.application.port.in.RefreshTokenUseCase;
import org.example.nabat.application.port.in.RegisterUserUseCase;
import org.example.nabat.application.port.out.LoginAttemptPort;
import org.example.nabat.application.port.out.RefreshTokenStore;
import org.example.nabat.application.port.out.RequestContextPort;
import org.example.nabat.application.port.out.TokenProvider;
import org.example.nabat.application.port.out.UserRepository;
import org.example.nabat.domain.exception.AuthenticationFailedException;
import org.example.nabat.domain.exception.EmailAlreadyRegisteredException;
import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.UserId;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@Slf4j
public class AuthenticationService implements RegisterUserUseCase, LoginUserUseCase, RefreshTokenUseCase {

    /** One message for every login failure, so responses cannot be used to probe accounts. */
    private static final String INVALID_CREDENTIALS = "Invalid email or password";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private final RefreshTokenStore refreshTokenStore;
    private final LoginAttemptPort loginAttempts;
    private final RequestContextPort requestContext;

    public AuthenticationService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        TokenProvider tokenProvider,
        RefreshTokenStore refreshTokenStore,
        LoginAttemptPort loginAttempts,
        RequestContextPort requestContext
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.refreshTokenStore = refreshTokenStore;
        this.loginAttempts = loginAttempts;
        this.requestContext = requestContext;
    }

    /**
     * Creates the account and returns it together with a freshly minted session.
     *
     * <p>Returns the tokens directly rather than leaving the caller to call
     * {@code login()} with the plaintext password again: that re-ran bcrypt and
     * re-queried the user for an identity this method had just created, and it meant
     * registration could fail at the login step for reasons unrelated to registering.
     */
    @Override
    @Transactional
    public RegistrationResult register(RegisterCommand command) {
        if (userRepository.existsByEmail(command.email())) {
            log.info("Registration attempt with an already-registered email");
            // A duplicate email is a conflict (409). It previously threw
            // IllegalArgumentException carrying the *success* text "Registration
            // submitted. Please verify your email." — a 400 with a reassuring message,
            // which neither prevented enumeration (201 vs 400 still distinguishes the
            // two cases) nor made any sense to the client.
            throw new EmailAlreadyRegisteredException();
        }

        String hashedPassword = passwordEncoder.encode(command.password());
        User user = userRepository.save(User.create(command.email(), hashedPassword, command.displayName()));

        return new RegistrationResult(
            user,
            tokenProvider.generateAccessToken(user),
            tokenProvider.generateRefreshToken(user),
            tokenProvider.getJwtExpiration()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public LoginUserUseCase.LoginResult login(LoginCommand command) {
        String clientIp = requestContext.clientIp();

        User user = userRepository.findByEmail(command.email())
                .orElseThrow(() -> {
                    loginAttempts.recordFailure(command.email(), clientIp);
                    return new AuthenticationFailedException(INVALID_CREDENTIALS);
                });

        if (!passwordEncoder.matches(command.password(), user.password())) {
            loginAttempts.recordFailure(command.email(), clientIp);
            throw new AuthenticationFailedException(INVALID_CREDENTIALS);
        }

        if (!user.enabled()) {
            loginAttempts.recordFailure(command.email(), clientIp);
            throw new AuthenticationFailedException("User account is disabled");
        }

        loginAttempts.recordSuccess(command.email(), clientIp);

        return new LoginUserUseCase.LoginResult(
                tokenProvider.generateAccessToken(user),
                tokenProvider.generateRefreshToken(user),
                tokenProvider.getJwtExpiration(),
                user
        );
    }

    /**
     * Exchanges a refresh token for a new pair. Each refresh token is single-use.
     *
     * <p>Previously the presented token stayed valid for its full seven-day lifetime
     * after being exchanged, so a stolen one granted indefinite access and individual
     * sessions could not be revoked.
     */
    @Override
    @Transactional(readOnly = true)
    public RefreshTokenUseCase.AuthTokens refresh(String refreshToken) {
        TokenProvider.RefreshTokenClaims claims = tokenProvider.parseRefreshToken(refreshToken)
            .orElseThrow(() -> new AuthenticationFailedException("Invalid refresh token"));

        // Looked up by id, not by the email in the subject: email is mutable, so a
        // subject-based lookup breaks as soon as a user changes address.
        User user = userRepository.findById(UserId.of(claims.userId()))
            .orElseThrow(() -> new AuthenticationFailedException("Invalid refresh token"));

        if (!user.enabled()) {
            throw new AuthenticationFailedException("User account is disabled");
        }
        if (claims.tokenVersion() != user.tokenVersion()) {
            // Password reset or explicit revocation happened after this token was issued.
            throw new AuthenticationFailedException("Session is no longer valid");
        }
        if (!refreshTokenStore.consume(claims.tokenId(), tokenProvider.refreshTokenLifetime())) {
            // Replay of an already-exchanged token: either a stolen token being reused,
            // or the legitimate holder retrying. Both warrant re-authentication.
            log.warn("Refresh token replay detected for user {}", user.id().value());
            throw new AuthenticationFailedException("Refresh token has already been used");
        }

        return new RefreshTokenUseCase.AuthTokens(
            tokenProvider.generateAccessToken(user),
            tokenProvider.generateRefreshToken(user),
            tokenProvider.getJwtExpiration()
        );
    }
}
