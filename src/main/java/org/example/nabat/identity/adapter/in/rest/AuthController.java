package org.example.nabat.identity.adapter.in.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.example.nabat.identity.application.port.in.ForgotPasswordUseCase;
import org.example.nabat.identity.application.port.in.LoginUserUseCase;
import org.example.nabat.identity.application.port.in.RefreshTokenUseCase;
import org.example.nabat.identity.application.port.in.RegisterUserUseCase;
import org.example.nabat.identity.application.port.in.ResetPasswordUseCase;
import org.example.nabat.identity.application.port.in.VerifyEmailUseCase;
import org.example.nabat.identity.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

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
        // Returns the session directly — no second round-trip through login() with the
        // plaintext password, which used to re-run bcrypt for an identity just created.
        RegisterUserUseCase.RegistrationResult result = registerUserUseCase.register(request.toCommand());

        // Best-effort: a dead SMTP relay must not fail an otherwise-successful signup.
        // The user can request a new verification email later.
        try {
            verifyEmailUseCase.sendVerificationEmail(result.user().id());
        } catch (RuntimeException e) {
            // Previously an empty catch block whose comment said "Log but don't block"
            // while logging nothing at all, so a permanently broken mail setup was silent.
            log.warn("Could not send verification email to the new account {}: {}",
                result.user().id().value(), e.getMessage(), e);
        }

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
        // Always 200, whether or not the address is registered, to prevent enumeration.
        return ResponseEntity.ok(new MessageResponse(
                "If that email is registered, a reset link has been sent"));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request) {
        resetPasswordUseCase.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.ok(new MessageResponse(
                "Password reset successfully. Existing sessions have been signed out."));
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

    /**
     * The authenticated user's own profile.
     *
     * <p>No null check on the principal: {@code /api/v1/**} requires authentication,
     * so an unauthenticated caller is rejected by the filter chain and never reaches
     * this method. The previous manual 401 was unreachable.
     */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> getCurrentUser(@AuthenticationPrincipal User user) {
        return ResponseEntity.ok(UserResponse.from(user));
    }

    public record RefreshTokenRequest(
        @NotBlank(message = "refreshToken is required") String refreshToken
    ) {}

    public record RefreshTokenResponse(
        String accessToken,
        String refreshToken,
        long expiresIn
    ) {}

    public record MessageResponse(String message) {}
}
