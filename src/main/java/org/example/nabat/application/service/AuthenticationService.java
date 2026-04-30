package org.example.nabat.application.service;

import lombok.extern.slf4j.Slf4j;
import org.example.nabat.adapter.in.security.LoginAttemptTracker;
import org.example.nabat.adapter.in.security.RequestContextHelper;
import org.example.nabat.application.UseCase;
import org.example.nabat.application.port.in.LoginUserUseCase;
import org.example.nabat.application.port.in.RefreshTokenUseCase;
import org.example.nabat.application.port.in.RegisterUserUseCase;
import org.example.nabat.application.port.out.TokenProvider;
import org.example.nabat.application.port.out.UserRepository;
import org.example.nabat.domain.model.User;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@UseCase
@Slf4j
public class AuthenticationService implements RegisterUserUseCase, LoginUserUseCase, RefreshTokenUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenProvider tokenProvider;
    private final LoginAttemptTracker loginAttemptTracker;

    public AuthenticationService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        TokenProvider tokenProvider,
        LoginAttemptTracker loginAttemptTracker
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.loginAttemptTracker = loginAttemptTracker;
    }

    @Override
    @Transactional
    public User register(RegisterCommand command) {
        if (userRepository.existsByEmail(command.email())) {
            log.info("Registration attempt with existing email: {}", command.email());
            throw new IllegalArgumentException("Registration submitted. Please verify your email.");
        }

        String hashedPassword = passwordEncoder.encode(command.password());
        User user = User.create(command.email(), hashedPassword, command.displayName());
        
        return userRepository.save(user);
    }

    @Override
    @Transactional(readOnly = true)
    public LoginUserUseCase.LoginResult login(LoginCommand command) {
        String clientIp = RequestContextHelper.getClientIp();

        User user = userRepository.findByEmail(command.email())
                .orElseThrow(() -> {
                    loginAttemptTracker.recordFailedAttempt(command.email(), clientIp);
                    return new BadCredentialsException("Invalid email or password");
                });

        if (!passwordEncoder.matches(command.password(), user.password())) {
            loginAttemptTracker.recordFailedAttempt(command.email(), clientIp);
            throw new BadCredentialsException("Invalid email or password");
        }

        if (!user.enabled()) {
            loginAttemptTracker.recordFailedAttempt(command.email(), clientIp);
            throw new BadCredentialsException("User account is disabled");
        }

        String accessToken = tokenProvider.generateAccessToken(user);
        String refreshToken = tokenProvider.generateRefreshToken(user);

        return new LoginUserUseCase.LoginResult(
                accessToken,
                refreshToken,
                tokenProvider.getJwtExpiration(),
                user
        );
    }

    @Override
    @Transactional(readOnly = true)
    public RefreshTokenUseCase.AuthTokens refresh(String refreshToken) {
        if (!tokenProvider.validateToken(refreshToken)) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        if (!tokenProvider.isRefreshToken(refreshToken)) {
            throw new BadCredentialsException("Token is not a refresh token");
        }

        String email = tokenProvider.getEmailFromToken(refreshToken);
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new BadCredentialsException("User not found"));

        if (!user.enabled()) {
            throw new BadCredentialsException("User account is disabled");
        }

        String newAccessToken = tokenProvider.generateAccessToken(user);
        String newRefreshToken = tokenProvider.generateRefreshToken(user);

        return new RefreshTokenUseCase.AuthTokens(
            newAccessToken,
            newRefreshToken,
            tokenProvider.getJwtExpiration()
        );
    }
}
