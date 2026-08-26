package org.example.nabat.identity.adapter.out.persistence;

import org.example.nabat.PostgresTestSupport;
import org.example.nabat.identity.domain.Role;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.identity.domain.VerificationToken;
import org.example.nabat.identity.domain.VerificationTokenType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The test that was missing.
 *
 * <p>Email verification shipped with a column sized for a UUID and an id that is a
 * Base64url-encoded SHA-256 hash — 43 characters into VARCHAR(36) — so every insert was
 * rejected and no token was ever stored. `POST /auth/verify` and `POST /auth/reset-password`
 * could not succeed at all.
 *
 * <p>Nothing failed, because nothing wrote one: {@code EmailVerificationServiceTest} mocks the
 * repository, and the integration tests register users without verifying them. Ten passing
 * unit tests described a feature that could not work. This one writes a real token produced by
 * the real factory, which is the only way that class of defect surfaces.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class VerificationTokenJpaRepositoryTest extends PostgresTestSupport {

    @Autowired
    private VerificationTokenJpaRepository tokenRepository;

    @Autowired
    private UserJpaRepository userRepository;

    private UserId userId;

    @BeforeEach
    void setUp() {
        tokenRepository.deleteAll();
        userRepository.deleteAll();

        // A real row: verification_tokens.user_id carries a foreign key.
        UserJpaEntity user = new UserJpaEntity();
        user.setId(UUID.randomUUID());
        user.setEmail("token-owner@example.com");
        user.setPassword("hash");
        user.setDisplayName("Token Owner");
        user.setRole(Role.USER);
        user.setEnabled(true);
        user.setEmailVerified(false);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        user.setNotificationRadiusKm(5);
        userRepository.save(user);
        userId = UserId.of(user.getId());
    }

    @Test
    void storesATokenWhoseIdIsAHashRatherThanAUuid() {
        VerificationToken.Issued issued = VerificationToken.createEmailVerification(userId);

        // The assertion that names the bug: 43 characters, into a column that allowed 36.
        assertThat(issued.token().id())
            .as("Base64url SHA-256 without padding")
            .hasSize(43);

        tokenRepository.save(VerificationTokenJpaEntity.from(issued.token()));

        Optional<VerificationTokenJpaEntity> found =
            tokenRepository.findByIdAndType(issued.token().id(), VerificationTokenType.EMAIL_VERIFICATION);

        assertThat(found).isPresent();
        assertThat(found.get().getUserId()).isEqualTo(userId.value());
        assertThat(found.get().isUsed()).isFalse();
    }

    @Test
    void storesAPasswordResetTokenToo() {
        VerificationToken.Issued issued = VerificationToken.createPasswordReset(userId);

        tokenRepository.save(VerificationTokenJpaEntity.from(issued.token()));

        assertThat(tokenRepository.findByIdAndType(
            issued.token().id(), VerificationTokenType.PASSWORD_RESET)).isPresent();
    }

    /**
     * The lookup is by the hash of what the user presents. A token is found only by the exact
     * stored id, so the emailed secret cannot be guessed from a prefix.
     */
    @Test
    void doesNotFindATokenByTheWrongTypeOrAPartialId() {
        VerificationToken.Issued issued = VerificationToken.createEmailVerification(userId);
        tokenRepository.save(VerificationTokenJpaEntity.from(issued.token()));

        assertThat(tokenRepository.findByIdAndType(
            issued.token().id(), VerificationTokenType.PASSWORD_RESET)).isEmpty();
        assertThat(tokenRepository.findByIdAndType(
            issued.token().id().substring(0, 20), VerificationTokenType.EMAIL_VERIFICATION)).isEmpty();
    }

    @Test
    void marksATokenUsedWithoutLosingIt() {
        VerificationToken.Issued issued = VerificationToken.createEmailVerification(userId);
        tokenRepository.save(VerificationTokenJpaEntity.from(issued.token()));

        tokenRepository.save(VerificationTokenJpaEntity.from(issued.token().markUsed()));

        Optional<VerificationTokenJpaEntity> found = tokenRepository.findByIdAndType(
            issued.token().id(), VerificationTokenType.EMAIL_VERIFICATION);
        assertThat(found).isPresent();
        assertThat(found.get().isUsed()).isTrue();
    }
}
