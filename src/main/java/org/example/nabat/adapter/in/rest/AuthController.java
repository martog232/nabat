package org.example.nabat.adapter.in.rest;

import jakarta.validation.Valid;
import org.example.nabat.application.port.in.ForgotPasswordUseCase;
import org.example.nabat.application.port.in.LoginUserUseCase;
import org.example.nabat.application.port.in.RefreshTokenUseCase;
import org.example.nabat.application.port.in.RegisterUserUseCase;
import org.example.nabat.application.port.in.ResetPasswordUseCase;
import org.example.nabat.application.port.in.VerifyEmailUseCase;
import org.example.nabat.domain.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RegisterUserUseCase registerUserUseCase;
    private final LoginUserUseCase loginUserUseCase;
    private final RefreshTokenUseCase refreshTokenUseCase;
    private final VerifyEmailUseCase verifyEmailUseCase;
    private final ForgotPasswordUseCase forgotPasswordUseCase;
    private final ResetPasswordUseCase resetPasswordUseCase;

    public AuthController(
        RegisterUserUseCase registerUserUseCase,
        LoginUserUseCase loginUserUseCase,
        RefreshTokenUseCase refreshTokenUseCase,
        VerifyEmailUseCase verifyEmailUseCase,
        ForgotPasswordUseCase forgotPasswordUseCase,
        ResetPasswordUseCase resetPasswordUseCase
    ) {
        this.registerUserUseCase = registerUserUseCase;
        this.loginUserUseCase = loginUserUseCase;
        this.refreshTokenUseCase = refreshTokenUseCase;
        this.verifyEmailUseCase = verifyEmailUseCase;
        this.forgotPasswordUseCase = forgotPasswordUseCase;
        this.resetPasswordUseCase = resetPasswordUseCase;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        User user = registerUserUseCase.register(request.toCommand());

        // Send verification email asynchronously — not a blocking failure
        try {
            verifyEmailUseCase.sendVerificationEmail(user.id());
        } catch (Exception e) {
            // Log but don't block registration if email fails
        }

        // Auto-login after registration
        LoginUserUseCase.LoginResult result = loginUserUseCase.login(
            new LoginUserUseCase.LoginCommand(request.email(), request.password())
        );

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(AuthResponse.from(result));
    }

    @PostMapping("/verify")
    public ResponseEntity<MessageResponse> verifyEmail(@RequestParam String token) {
        verifyEmailUseCase.verifyEmail(token);
        return ResponseEntity.ok(new MessageResponse("Email verified successfully"));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        forgotPasswordUseCase.sendPasswordReset(request.email());
        // Always return 200 to prevent user enumeration
        return ResponseEntity.ok(new MessageResponse(
                "If that email is registered, a reset link has been sent"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        resetPasswordUseCase.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(new MessageResponse("Password reset successfully"));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        LoginUserUseCase.LoginResult result = loginUserUseCase.login(request.toCommand());
        return ResponseEntity.ok(AuthResponse.from(result));
    }

    @PostMapping("/refresh")
    public ResponseEntity<RefreshTokenResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        RefreshTokenUseCase.AuthTokens tokens = refreshTokenUseCase.refresh(request.refreshToken());
        
        return ResponseEntity.ok(new RefreshTokenResponse(
            tokens.accessToken(),
            tokens.refreshToken(),
            tokens.expiresIn()
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal User user) {
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(UserResponse.from(user));
    }

    public record RefreshTokenRequest(String refreshToken) {}
    
    public record RefreshTokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
    ) {}

    public record MessageResponse(String message) {}
}
