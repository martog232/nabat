package org.example.nabat.identity.application.port.in;

import org.example.nabat.identity.domain.Role;
import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;

/**
 * Changing what another account may do, and switching one off.
 *
 * <p>Separate from {@link UpdateUserPreferencesUseCase}, which is a user acting on itself.
 * Everything here is one account acting on another, so every method takes the actor as well
 * as the target: the authorisation rules are about the relationship between the two, not
 * about the caller alone, and that is not something a {@code @PreAuthorize} expression can
 * express.
 */
public interface AdministerUsersUseCase {

    /**
     * One page of accounts for the admin screen, newest first.
     *
     * <p>Takes the actor like everything else here, and for the same reason: the controller's
     * {@code @PreAuthorize} reads the role in the token, which is as old as the token, while
     * this re-reads the row. An admin demoted a minute ago still presents {@code ROLE_ADMIN}.
     *
     * @param page zero-based; {@code size} is capped by the caller
     */
    UserPage listUsers(UserId actorId, int page, int size);

    /**
     * Deliberately not {@code UserRepository.UserPage}, though the shape is the same.
     *
     * <p>{@code ArchitectureTest} forbids a controller from importing an out-port, and a
     * controller has to name this type to return it. Three lines of mapping in the service is
     * the price of the rule holding.
     */
    record UserPage(java.util.List<User> users, long total) {
    }

    /**
     * Assigns a role.
     *
     * <p>Refuses when the actor is the target. An admin demoting themselves is how an
     * installation ends up with no one who can hand the role back — the platform has no
     * break-glass path, so the only safe answer is that the last step must be taken by
     * someone else.
     */
    User changeRole(UserId actorId, UserId targetId, Role newRole);

    /**
     * Enables or disables an account.
     *
     * <p>Disabling bumps the target's token version, so sessions already in flight stop
     * working immediately rather than at token expiry — including WebSocket handshakes, which
     * re-check the account. Refuses self-targeting for the same reason as
     * {@link #changeRole}.
     */
    User setEnabled(UserId actorId, UserId targetId, boolean enabled);
}
