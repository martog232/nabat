package org.example.nabat.adapter.out.persistence;

import org.example.nabat.domain.model.Role;
import org.example.nabat.PostgresTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserJpaRepositoryTest extends PostgresTestSupport {

    @Autowired
    private UserJpaRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void findByEmailAndExistsByEmailWorkAsExpected() {
        String email = "repo-user@example.com";
        UserJpaEntity user = new UserJpaEntity();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setPassword("hash");
        user.setDisplayName("Repo User");
        user.setRole(Role.USER);
        user.setEnabled(true);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());

        userRepository.saveAndFlush(user);

        Optional<UserJpaEntity> found = userRepository.findByEmail(email);

        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo(email);
        assertThat(userRepository.existsByEmail(email)).isTrue();
        assertThat(userRepository.existsByEmail("missing@example.com")).isFalse();
    }
}

