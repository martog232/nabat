package org.example.nabat.identity.application.port.in;

import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;

import java.util.List;

/**
 * An authority's own staff, managed by the authority.
 *
 * <p>One shared account per authority meant nobody could tell which person acknowledged or
 * rejected a report. The shared account — the one that signs in with the authority's mailbox —
 * is its <em>lead</em>: it invites colleagues, who get accounts of their own and act under their
 * own names, and it takes the role away from those who leave. Only the lead does either: an
 * official cannot add officials, or one shared password would still open the door to everyone.
 */
public interface AuthorityMembersUseCase {

    /** Everyone acting for the lead's authority, the lead included, oldest account first. */
    List<User> members(User lead);

    /**
     * Creates an official account for {@code email} in the lead's authority and mails it an
     * invitation to set its password.
     *
     * @throws org.example.nabat.identity.domain.EmailAlreadyRegisteredException if the address has an
     *         account — promoting someone else's account is not the lead's to do
     */
    User invite(User lead, String email, String displayName);

    /**
     * Takes the role from a member: they become a citizen, and lose the authority's reports at
     * once. Not the lead, which is the authority's own account.
     */
    User remove(User lead, UserId memberId);
}
