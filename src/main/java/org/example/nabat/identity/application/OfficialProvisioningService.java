package org.example.nabat.identity.application;

import lombok.extern.slf4j.Slf4j;
import org.example.nabat.identity.application.port.in.ProvisionOfficialsUseCase;
import org.example.nabat.identity.application.port.out.AuthorityDirectoryPort;
import org.example.nabat.identity.application.port.out.AuthorityDirectoryPort.AuthorityEntry;
import org.example.nabat.identity.application.port.out.UserRepository;
import org.example.nabat.identity.domain.PasswordPolicy;
import org.example.nabat.identity.domain.User;
import org.example.nabat.shared.UseCase;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@UseCase
@Slf4j
public class OfficialProvisioningService implements ProvisionOfficialsUseCase {

    private final UserRepository userRepository;
    private final AuthorityDirectoryPort authorities;
    private final PasswordEncoder passwordEncoder;

    public OfficialProvisioningService(
        UserRepository userRepository,
        AuthorityDirectoryPort authorities,
        PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.authorities = authorities;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public Result provision(String password) {
        PasswordPolicy.check(password).ifPresent(violation -> {
            throw new IllegalArgumentException("Official account password rejected: " + violation);
        });

        int created = 0;
        int present = 0;
        List<String> conflicts = new ArrayList<>();
        for (AuthorityEntry authority : authorities.all()) {
            Optional<User> existing = userRepository.findByEmail(authority.mailbox());
            if (existing.isEmpty()) {
                userRepository.save(official(authority, password));
                created++;
            } else if (existing.get().actsFor(authority.code())) {
                present++;
            } else {
                log.warn("{} already belongs to an account that does not act for {}; left alone",
                    authority.mailbox(), authority.code());
                conflicts.add(authority.code());
            }
        }
        return new Result(created, present, List.copyOf(conflicts));
    }

    /**
     * Verified from the start. The address is the authority's own mailbox, set by whoever runs
     * nabat rather than claimed by a stranger, and the verification link proves only the
     * latter.
     */
    private User official(AuthorityEntry authority, String password) {
        return User.create(authority.mailbox(), passwordEncoder.encode(password), authority.name())
            .verifyEmail()
            .appointTo(authority.code());
    }
}
