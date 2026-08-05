package org.example.nabat.identity.application.port.out;

import org.example.nabat.shared.domain.Location;
import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;

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
