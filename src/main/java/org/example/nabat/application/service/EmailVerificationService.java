package org.example.nabat.application.service;

import org.example.nabat.application.UseCase;
import org.example.nabat.application.port.in.ForgotPasswordUseCase;
import org.example.nabat.application.port.in.ResetPasswordUseCase;
import org.example.nabat.application.port.in.VerifyEmailUseCase;
import org.example.nabat.application.port.out.EmailSender;
import org.example.nabat.application.port.out.UserRepository;
import org.example.nabat.application.port.out.VerificationTokenRepository;
import org.example.nabat.domain.exception.UserNotFoundException;
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

    /** Deliberately identical for every failure mode — see {@link #consume}. */
    private static final String INVALID_TOKEN = "Invalid or expired token";

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
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (user.emailVerified()) {
            log.debug("User already verified — skipping token creation");
            return;
        }

        // Invalidate any previous token before issuing a fresh one
        tokenRepository.deleteByUserId(userId, VerificationTokenType.EMAIL_VERIFICATION);
        VerificationToken.Issued issued = VerificationToken.createEmailVerification(userId);
        tokenRepository.save(issued.token());

        // The raw secret goes in the email; only its hash was persisted above.
        emailSender.sendVerificationEmail(user.email(), user.displayName(), issued.rawValue());
        log.info("Verification email queued for user {}", userId.value());
    }

    @Override
    @Transactional
    public void verifyEmail(String rawToken) {
        VerificationToken token = consume(rawToken, VerificationTokenType.EMAIL_VERIFICATION);

        User user = userRepository.findById(token.userId())
                .orElseThrow(() -> new UserNotFoundException(token.userId()));

        userRepository.save(user.verifyEmail());
        tokenRepository.save(token.markUsed());
        log.info("Email verified for user {}", user.id().value());
    }

    // ── ForgotPasswordUseCase ────────────────────────────────────────────────

    @Override
    @Transactional
    public void sendPasswordReset(String email) {
        // Always the same outcome regardless of whether the account exists, so this
        // endpoint cannot be used to enumerate registered addresses.
        userRepository.findByEmail(email).ifPresentOrElse(user -> {
            tokenRepository.deleteByUserId(user.id(), VerificationTokenType.PASSWORD_RESET);
            VerificationToken.Issued issued = VerificationToken.createPasswordReset(user.id());
            tokenRepository.save(issued.token());
            emailSender.sendPasswordResetEmail(user.email(), user.displayName(), issued.rawValue());
            log.info("Password-reset email queued for user {}", user.id().value());
        }, () -> log.debug("Forgot-password requested for unknown email — ignoring"));
    }

    // ── ResetPasswordUseCase ─────────────────────────────────────────────────

    @Override
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        VerificationToken token = consume(rawToken, VerificationTokenType.PASSWORD_RESET);

        User user = userRepository.findById(token.userId())
                .orElseThrow(() -> new UserNotFoundException(token.userId()));

        // withPassword also bumps tokenVersion, which invalidates every access and
        // refresh token already issued to this user. Without that, a reset prompted by
        // a suspected compromise left the attacker's existing session working — for up
        // to the refresh-token lifetime.
        userRepository.save(user.withPassword(passwordEncoder.encode(newPassword)));
        tokenRepository.save(token.markUsed());
        log.info("Password reset for user {}; existing sessions invalidated", user.id().value());
    }

    /**
     * Looks up a presented token by its hash and checks it is usable.
     *
     * <p>Unknown, already-used and expired tokens all raise the same message, so the
     * response cannot distinguish "no such token" from "that token has expired" —
     * which would otherwise confirm to an attacker that a guessed value once existed.
     */
    private VerificationToken consume(String rawToken, VerificationTokenType type) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException(INVALID_TOKEN);
        }

        VerificationToken token = tokenRepository
                .findByIdAndType(VerificationToken.hash(rawToken.trim()), type)
                .orElseThrow(() -> new IllegalArgumentException(INVALID_TOKEN));

        if (token.used() || token.isExpired()) {
            throw new IllegalArgumentException(INVALID_TOKEN);
        }
        return token;
    }
}
