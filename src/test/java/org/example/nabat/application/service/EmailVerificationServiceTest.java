package org.example.nabat.application.service;

import org.example.nabat.application.port.out.EmailSender;
import org.example.nabat.application.port.out.UserRepository;
import org.example.nabat.application.port.out.VerificationTokenRepository;
import org.example.nabat.domain.model.Role;
import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.domain.model.VerificationToken;
import org.example.nabat.domain.model.VerificationTokenType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private VerificationTokenRepository tokenRepository;
    @Mock private EmailSender emailSender;

    private PasswordEncoder passwordEncoder;
    private EmailVerificationService service;

    private final UserId userId = UserId.generate();
    private final String email = "alice@example.com";

    private User user(boolean verified) {
        return new User(userId, email, "hash", "Alice", Role.USER, true, verified,
                Instant.now(), Instant.now(), 5, null, null, null);
    }

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        service = new EmailVerificationService(userRepository, tokenRepository, emailSender, passwordEncoder);
    }

    // ── sendVerificationEmail ────────────────────────────────────────────────

    @Test
    void sendVerificationEmail_createsTokenAndSendsEmail() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(false)));
        VerificationToken saved = VerificationToken.createEmailVerification(userId);
        when(tokenRepository.save(any())).thenReturn(saved);

        service.sendVerificationEmail(userId);

        verify(tokenRepository).deleteByUserId(userId, VerificationTokenType.EMAIL_VERIFICATION);
        verify(tokenRepository).save(any());
        verify(emailSender).sendVerificationEmail(eq(email), eq("Alice"), any());
    }

    @Test
    void sendVerificationEmail_skipsWhenAlreadyVerified() {
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(true)));

        service.sendVerificationEmail(userId);

        verifyNoInteractions(tokenRepository, emailSender);
    }

    // ── verifyEmail ──────────────────────────────────────────────────────────

    @Test
    void verifyEmail_marksUserVerified() {
        VerificationToken token = VerificationToken.createEmailVerification(userId);
        when(tokenRepository.findByIdAndType(token.id(), VerificationTokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(false)));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.verifyEmail(token.id());

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        assertTrue(savedUser.getValue().emailVerified());

        ArgumentCaptor<VerificationToken> savedToken = ArgumentCaptor.forClass(VerificationToken.class);
        verify(tokenRepository).save(savedToken.capture());
        assertTrue(savedToken.getValue().used());
    }

    @Test
    void verifyEmail_throwsForUnknownToken() {
        when(tokenRepository.findByIdAndType(any(), any())).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.verifyEmail("bad-token"));
    }

    @Test
    void verifyEmail_throwsForAlreadyUsedToken() {
        VerificationToken used = VerificationToken.createEmailVerification(userId).markUsed();
        when(tokenRepository.findByIdAndType(used.id(), VerificationTokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(used));
        assertThrows(IllegalArgumentException.class, () -> service.verifyEmail(used.id()));
    }

    @Test
    void verifyEmail_throwsForExpiredToken() {
        // Construct an already-expired token by creating a verification token and replacing its
        // expiresAt with a past instant via the copy constructor
        VerificationToken fresh = VerificationToken.createEmailVerification(userId);
        Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
        VerificationToken expired = new VerificationToken(
                fresh.id(), fresh.userId(), fresh.type(), past, false, fresh.createdAt());
        when(tokenRepository.findByIdAndType(expired.id(), VerificationTokenType.EMAIL_VERIFICATION))
                .thenReturn(Optional.of(expired));
        assertThrows(IllegalArgumentException.class, () -> service.verifyEmail(expired.id()));
    }

    // ── sendPasswordReset ────────────────────────────────────────────────────

    @Test
    void sendPasswordReset_sendsEmailForKnownUser() {
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user(false)));
        VerificationToken resetToken = VerificationToken.createPasswordReset(userId);
        when(tokenRepository.save(any())).thenReturn(resetToken);

        service.sendPasswordReset(email);

        verify(tokenRepository).deleteByUserId(userId, VerificationTokenType.PASSWORD_RESET);
        verify(emailSender).sendPasswordResetEmail(eq(email), eq("Alice"), any());
    }

    @Test
    void sendPasswordReset_doesNothingForUnknownEmail() {
        when(userRepository.findByEmail("unknown@x.y")).thenReturn(Optional.empty());

        service.sendPasswordReset("unknown@x.y");

        verifyNoInteractions(tokenRepository, emailSender);
    }

    // ── resetPassword ────────────────────────────────────────────────────────

    @Test
    void resetPassword_updatesUserPassword() {
        VerificationToken token = VerificationToken.createPasswordReset(userId);
        when(tokenRepository.findByIdAndType(token.id(), VerificationTokenType.PASSWORD_RESET))
                .thenReturn(Optional.of(token));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user(false)));
        when(userRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        when(tokenRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.resetPassword(token.id(), "newSecurePass");

        ArgumentCaptor<User> savedUser = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(savedUser.capture());
        // Password must have been re-encoded (not stored in plain text)
        assertNotEquals("newSecurePass", savedUser.getValue().password());
        assertTrue(passwordEncoder.matches("newSecurePass", savedUser.getValue().password()));
    }

    @Test
    void resetPassword_throwsForExpiredToken() {
        VerificationToken fresh = VerificationToken.createPasswordReset(userId);
        Instant past = Instant.now().minus(2, ChronoUnit.HOURS);
        VerificationToken expired = new VerificationToken(
                fresh.id(), fresh.userId(), fresh.type(), past, false, fresh.createdAt());
        when(tokenRepository.findByIdAndType(expired.id(), VerificationTokenType.PASSWORD_RESET))
                .thenReturn(Optional.of(expired));
        assertThrows(IllegalArgumentException.class,
                () -> service.resetPassword(expired.id(), "pass"));
    }
}
