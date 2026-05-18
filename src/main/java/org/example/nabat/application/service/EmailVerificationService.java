package org.example.nabat.application.service;

import org.example.nabat.application.UseCase;
import org.example.nabat.application.port.in.ForgotPasswordUseCase;
import org.example.nabat.application.port.in.ResetPasswordUseCase;
import org.example.nabat.application.port.in.VerifyEmailUseCase;
import org.example.nabat.application.port.out.EmailSender;
import org.example.nabat.application.port.out.UserRepository;
import org.example.nabat.application.port.out.VerificationTokenRepository;
import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.domain.model.VerificationToken;
import org.example.nabat.domain.model.VerificationTokenType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class EmailVerificationService
        implements VerifyEmailUseCase, ForgotPasswordUseCase, ResetPasswordUseCase {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    private final UserRepository userRepository;
    private final VerificationTokenRepository tokenRepository;
    private final EmailSender emailSender;
    private final PasswordEncoder passwordEncoder;

    public EmailVerificationService(
            UserRepository userRepository,
            VerificationTokenRepository tokenRepository,
            EmailSender emailSender,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.tokenRepository = tokenRepository;
        this.emailSender = emailSender;
        this.passwordEncoder = passwordEncoder;
    }

    // ── VerifyEmailUseCase ───────────────────────────────────────────────────

    @Override
    @Transactional
    public void sendVerificationEmail(UserId userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (user.emailVerified()) {
            log.debug("User {} already verified — skipping token creation", userId.value());
            return;
        }

        // Invalidate any previous token before issuing a fresh one
        tokenRepository.deleteByUserId(userId, VerificationTokenType.EMAIL_VERIFICATION);
        VerificationToken token = tokenRepository.save(
                VerificationToken.createEmailVerification(userId));

        emailSender.sendVerificationEmail(user.email(), user.displayName(), token.id());
        log.info("Verification email queued for {}", user.email());
    }

    @Override
    @Transactional
    public void verifyEmail(String tokenId) {
        VerificationToken token = tokenRepository
                .findByIdAndType(tokenId, VerificationTokenType.EMAIL_VERIFICATION)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or unknown verification token"));

        if (token.used()) {
            throw new IllegalArgumentException("Verification token has already been used");
        }
        if (token.isExpired()) {
            throw new IllegalArgumentException("Verification token has expired");
        }

        User user = userRepository.findById(token.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        userRepository.save(user.verifyEmail());
        tokenRepository.save(token.markUsed());
        log.info("Email verified for user {}", user.email());
    }

    // ── ForgotPasswordUseCase ────────────────────────────────────────────────

    @Override
    @Transactional
    public void sendPasswordReset(String email) {
        // Always return the same success response regardless of whether the account
        // exists, to prevent user enumeration.
        userRepository.findByEmail(email).ifPresentOrElse(user -> {
            tokenRepository.deleteByUserId(user.id(), VerificationTokenType.PASSWORD_RESET);
            VerificationToken token = tokenRepository.save(
                    VerificationToken.createPasswordReset(user.id()));
            emailSender.sendPasswordResetEmail(user.email(), user.displayName(), token.id());
            log.info("Password-reset email queued for {}", user.email());
        }, () -> log.debug("Forgot-password requested for unknown email — ignoring"));
    }

    // ── ResetPasswordUseCase ─────────────────────────────────────────────────

    @Override
    @Transactional
    public void resetPassword(String tokenId, String newPassword) {
        VerificationToken token = tokenRepository
                .findByIdAndType(tokenId, VerificationTokenType.PASSWORD_RESET)
                .orElseThrow(() -> new IllegalArgumentException("Invalid or unknown reset token"));

        if (token.used()) {
            throw new IllegalArgumentException("Reset token has already been used");
        }
        if (token.isExpired()) {
            throw new IllegalArgumentException("Reset token has expired");
        }

        User user = userRepository.findById(token.userId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        userRepository.save(user.withPassword(passwordEncoder.encode(newPassword)));
        tokenRepository.save(token.markUsed());
        log.info("Password reset for user {}", user.email());
    }
}

