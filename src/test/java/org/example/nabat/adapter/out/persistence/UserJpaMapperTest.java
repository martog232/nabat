package org.example.nabat.adapter.out.persistence;

import org.example.nabat.domain.model.Role;
import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class UserJpaMapperTest {

    private final UserJpaMapper mapper = new UserJpaMapper() {
    };

    @Test
    void toEntityMapsUserIdToUuid() {
        UUID id = UUID.randomUUID();
        User user = new User(
            UserId.of(id),
            "mapper@example.com",
            "hash",
            "Mapper User",
            Role.USER,
            true,
            false,
            Instant.now(),
            Instant.now()
        );

        UserJpaEntity entity = mapper.toEntity(user);

        assertNotNull(entity);
        assertEquals(id, entity.getId());
        assertEquals(user.email(), entity.getEmail());
    }

    @Test
    void toDomainMapsUuidToUserId() {
        UUID id = UUID.randomUUID();
        UserJpaEntity entity = new UserJpaEntity();
        entity.setId(id);
        entity.setEmail("mapper@example.com");
        entity.setPassword("hash");
        entity.setDisplayName("Mapper User");
        entity.setRole(Role.USER);
        entity.setEnabled(true);
        entity.setEmailVerified(false);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        User user = mapper.toDomain(entity);

        assertNotNull(user);
        assertEquals(id, user.id().value());
        assertEquals(entity.getEmail(), user.email());
    }
}


