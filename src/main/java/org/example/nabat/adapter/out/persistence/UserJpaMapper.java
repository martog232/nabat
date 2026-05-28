package org.example.nabat.adapter.out.persistence;

import org.example.nabat.domain.model.User;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface UserJpaMapper {
    UserJpaEntity toEntity(User user);
    User toDomain(UserJpaEntity entity);
}
