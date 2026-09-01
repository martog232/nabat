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

    /**
     * One page of accounts, newest first, for the admin screen.
     *
     * <p>Paged rather than a full list for the same reason the nearby query is capped: this
     * is the only query in the application whose result grows with the number of registered
     * users, so an unbounded version is a table scan that ships every row to the browser and
     * gets slower for the rest of the platform's life.
     *
     * @param page zero-based
     * @return the rows and the total, which the caller needs to know there are more
     */
    UserPage findAll(int page, int size);

    /**
     * A page without Spring Data's {@code Page}, which is a framework type in a port that the
     * domain side has to implement against. Two fields is all the caller uses.
     */
    record UserPage(List<User> users, long total) {
    }
}
