package org.example.nabat.identity.application;

import org.example.nabat.identity.application.port.in.AdministerUsersUseCase;
import org.example.nabat.identity.application.port.out.AuthorityDirectoryPort;
import org.example.nabat.identity.application.port.out.UserRepository;
import org.example.nabat.identity.domain.Role;
import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.identity.domain.UserNotFoundException;
import org.example.nabat.shared.UseCase;
import org.example.nabat.shared.domain.NotAuthorizedException;
import org.springframework.transaction.annotation.Transactional;

@UseCase
public class UserAdministrationService implements AdministerUsersUseCase {

    private final UserRepository userRepository;
    private final AuthorityDirectoryPort authorities;

    public UserAdministrationService(UserRepository userRepository, AuthorityDirectoryPort authorities) {
        this.userRepository = userRepository;
        this.authorities = authorities;
    }

    @Override
    @Transactional(readOnly = true)
    public UserPage listUsers(UserId actorId, int page, int size) {
        requireActorWhoCanAdminister(actorId);

        UserRepository.UserPage found = userRepository.findAll(page, size);
        return new UserPage(found.users(), found.total());
    }

    @Override
    @Transactional
    public User changeRole(UserId actorId, UserId targetId, Role newRole, String authorityCode) {
        if (newRole == null) {
            throw new IllegalArgumentException("Role is required");
        }
        boolean official = newRole == Role.OFFICIAL;
        if (official == (authorityCode == null)) {
            throw new IllegalArgumentException(official
                ? "An official needs an authority"
                : "Only an official has an authority");
        }
        if (official && !authorities.exists(authorityCode)) {
            throw new IllegalArgumentException("No such authority: " + authorityCode);
        }

        User actor = requireActorWhoCanAdminister(actorId);
        requireNotSelf(actor, targetId, "change your own role");

        User target = requireUser(targetId);
        if (target.role() == newRole && java.util.Objects.equals(target.authorityCode(), authorityCode)) {
            // Idempotent rather than a conflict: the caller asked for a state that already
            // holds, and a 409 here would make a retry after a timeout look like a failure.
            return target;
        }

        return userRepository.save(official
            ? target.appointTo(authorityCode)
            : target.withRole(newRole));
    }

    @Override
    @Transactional
    public User setEnabled(UserId actorId, UserId targetId, boolean enabled) {
        User actor = requireActorWhoCanAdminister(actorId);
        requireNotSelf(actor, targetId, "disable your own account");

        User target = requireUser(targetId);
        if (target.enabled() == enabled) {
            return target;
        }

        // disable() bumps tokenVersion, so tokens already issued stop being accepted at once
        // — by the HTTP filter and by the WebSocket handshake, which asks the same question.
        // enable() deliberately does not: restoring an account should not also log out the
        // sessions of someone who was re-enabled seconds later.
        return userRepository.save(enabled ? target.enable() : target.disable());
    }

    /**
     * The actor is re-read from the database rather than trusted from the token.
     *
     * <p>A token carries the role it was minted with, so an admin demoted a minute ago still
     * presents {@code ROLE_ADMIN} until it expires. {@code @PreAuthorize} on the controller
     * checks that claim, which is the right first gate; this checks the current row, which is
     * the one that decides. The token-version check invalidates sessions on a credential
     * change, not on a role change, so without this a demotion would not take effect here.
     */
    private User requireActorWhoCanAdminister(UserId actorId) {
        User actor = requireUser(actorId);
        if (!actor.role().canAdministerUsers()) {
            throw new NotAuthorizedException("Only an admin can administer users");
        }
        return actor;
    }

    private void requireNotSelf(User actor, UserId targetId, String what) {
        if (actor.id().equals(targetId)) {
            throw new NotAuthorizedException("You cannot " + what);
        }
    }

    private User requireUser(UserId id) {
        return userRepository.findById(id).orElseThrow(() -> new UserNotFoundException(id));
    }
}
