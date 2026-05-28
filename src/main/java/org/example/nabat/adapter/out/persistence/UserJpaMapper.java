package org.example.nabat.adapter.out.persistence;

import org.example.nabat.domain.model.User;

public interface UserJpaMapper {
    default UserJpaEntity toEntity(User user) {
        return UserJpaEntity.from(user);
    }

    default User toDomain(UserJpaEntity entity) {
        return entity.toDomain();
    }
}
