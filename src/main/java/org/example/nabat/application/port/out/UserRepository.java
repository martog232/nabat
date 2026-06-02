package org.example.nabat.application.port.out;

import org.example.nabat.domain.model.Location;
import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.UserId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository {
    User save(User user);
    Optional<User> findById(UserId id);
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<UUID> findUsersNearLocation(Location alertLocation);
}
