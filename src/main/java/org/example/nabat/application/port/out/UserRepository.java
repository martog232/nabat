package org.example.nabat.application.port.out;

import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.UserId;

import java.util.Optional;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(UserId id);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
}
