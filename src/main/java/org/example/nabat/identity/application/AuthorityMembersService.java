package org.example.nabat.identity.application;

import org.example.nabat.identity.application.port.in.AuthorityMembersUseCase;
import org.example.nabat.identity.application.port.out.AuthorityDirectoryPort;
import org.example.nabat.identity.application.port.out.AuthorityDirectoryPort.AuthorityEntry;
import org.example.nabat.identity.application.port.out.EmailSender;
import org.example.nabat.identity.application.port.out.UserRepository;
import org.example.nabat.identity.application.port.out.VerificationTokenRepository;
import org.example.nabat.identity.domain.EmailAlreadyRegisteredException;
import org.example.nabat.identity.domain.Role;
import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.identity.domain.UserNotFoundException;
import org.example.nabat.identity.domain.VerificationToken;
import org.example.nabat.shared.UseCase;
import org.example.nabat.shared.domain.NotAuthorizedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;

@UseCase
public class AuthorityMembersService implements AuthorityMembersUseCase {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final AuthorityDirectoryPort authorities;
    private final VerificationTokenRepository tokens;
    private final EmailSender emailSender;
    private final PasswordEncoder passwordEncoder;

    public AuthorityMembersService(
        UserRepository users,
        AuthorityDirectoryPort authorities,
        VerificationTokenRepository tokens,
        EmailSender emailSender,
        PasswordEncoder passwordEncoder
    ) {
        this.users = users;
        this.authorities = authorities;
        this.tokens = tokens;
        this.emailSender = emailSender;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> members(User lead) {
        AuthorityEntry authority = requireLead(lead);
        return users.findByAuthority(authority.code());
    }

    @Override
    @Transactional
    public User invite(User lead, String email, String displayName) {
        AuthorityEntry authority = requireLead(lead);
        String address = email == null ? "" : email.strip();
        String name = displayName == null ? "" : displayName.strip();
        if (address.isEmpty() || name.isEmpty()) {
            throw new IllegalArgumentException("An invitation needs an address and a name");
        }
        if (users.existsByEmail(address)) {
            throw new EmailAlreadyRegisteredException();
        }

        // A password nobody knows, so the account cannot be signed into until the invitee sets
        // their own through the invitation.
        User member = users.save(User.create(address, passwordEncoder.encode(unguessable()), name)
            .appointTo(authority.code()));
        VerificationToken.Issued invitation = VerificationToken.createInvitation(member.id());
        tokens.save(invitation.token());
        emailSender.sendInvitationEmail(address, name, authority.name(), lead.displayName(), invitation.rawValue());
        return member;
    }

    @Override
    @Transactional
    public User remove(User lead, UserId memberId) {
        AuthorityEntry authority = requireLead(lead);
        User member = users.findById(memberId).orElseThrow(() -> new UserNotFoundException(memberId));
        if (!member.actsFor(authority.code())) {
            throw new NotAuthorizedException("Not a member of this authority");
        }
        if (member.id().equals(lead.id())) {
            throw new NotAuthorizedException("The authority's own account is not removed");
        }
        return users.save(member.withRole(Role.USER));
    }

    /**
     * The lead is the account that signs in with the authority's mailbox — the account the
     * provisioning made. Re-read, not trusted from the token: a role taken away a minute ago
     * still sits in a token minted before.
     */
    private AuthorityEntry requireLead(User actor) {
        User current = users.findById(actor.id()).orElseThrow(() -> new UserNotFoundException(actor.id()));
        if (!current.role().canActForAuthority()) {
            throw new NotAuthorizedException("Only an authority's own account manages its officials");
        }
        return authorities.all().stream()
            .filter(a -> a.code().equals(current.authorityCode()) && a.mailbox().equalsIgnoreCase(current.email()))
            .findFirst()
            .orElseThrow(() -> new NotAuthorizedException("Only an authority's own account manages its officials"));
    }

    private static String unguessable() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        // Letters and digits both, so it passes the policy if anything ever checks it.
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes) + "a1";
    }
}
