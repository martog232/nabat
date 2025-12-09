package org.example.nabat.application.service;

import org.example.nabat.adapter.in.security.JwtTokenProvider;
import org.example.nabat.application.UseCase;
import org.example.nabat.application.port.in.LoginUserUseCase;
import org.example.nabat.application.port.in.RefreshTokenUseCase;
import org.example.nabat.application.port.in.RegisterUserUseCase;
import org.example.nabat.application.port.out.UserRepository;
import org.example.nabat.domain.model.User;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;

@UseCase
public class AuthenticationService implements RegisterUserUseCase, LoginUserUseCase, RefreshTokenUseCase {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    public AuthenticationService(
        UserRepository userRepository,
        PasswordEncoder passwordEncoder,
        JwtTokenProvider jwtTokenProvider
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public User register(RegisterCommand command) {
        if (userRepository.existsByEmail(command.email())) {
            throw new IllegalArgumentException("Email already exists");
        }

        String hashedPassword = passwordEncoder.encode(command.password());
        User user = User.create(command.email(), hashedPassword, command.displayName());
        
        return userRepository.save(user);
    }

    @Override
    public LoginUserUseCase.LoginResult login(LoginCommand command) {
        User user = userRepository.findByEmail(command.email())
            .orElseThrow(() -> new BadCredentialsException("Invalid email or password"));

        if (!passwordEncoder.matches(command.password(), user.password())) {
            throw new BadCredentialsException("Invalid email or password");
        }

        if (!user.enabled()) {
            throw new BadCredentialsException("User account is disabled");
        }

        String accessToken = jwtTokenProvider.generateAccessToken(user);
        String refreshToken = jwtTokenProvider.generateRefreshToken(user);

        return new LoginUserUseCase.LoginResult(
            accessToken,
            refreshToken,
            jwtTokenProvider.getJwtExpiration(),
            user
        );
    }

    @Override
    public RefreshTokenUseCase.AuthTokens refresh(String refreshToken) {
        if (!jwtTokenProvider.validateToken(refreshToken)) {
            throw new BadCredentialsException("Invalid refresh token");
        }

        if (!jwtTokenProvider.isRefreshToken(refreshToken)) {
            throw new BadCredentialsException("Token is not a refresh token");
        }

        String email = jwtTokenProvider.getEmailFromToken(refreshToken);
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new BadCredentialsException("User not found"));

        if (!user.enabled()) {
            throw new BadCredentialsException("User account is disabled");
        }

        String newAccessToken = jwtTokenProvider.generateAccessToken(user);
        String newRefreshToken = jwtTokenProvider.generateRefreshToken(user);

        return new RefreshTokenUseCase.AuthTokens(
            newAccessToken,
            newRefreshToken,
            jwtTokenProvider.getJwtExpiration()
        );
    }
}
