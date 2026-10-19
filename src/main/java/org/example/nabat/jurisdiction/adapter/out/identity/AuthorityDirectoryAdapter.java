package org.example.nabat.jurisdiction.adapter.out.identity;

import org.example.nabat.identity.application.port.out.AuthorityDirectoryPort;
import org.example.nabat.jurisdiction.application.port.in.ListAuthoritiesUseCase;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Answers {@code identity}'s questions about authorities — the districts and the institutions —
 * from the ones this module holds.
 */
@Component
public class AuthorityDirectoryAdapter implements AuthorityDirectoryPort {

    private final ListAuthoritiesUseCase authorities;

    public AuthorityDirectoryAdapter(ListAuthoritiesUseCase authorities) {
        this.authorities = authorities;
    }

    @Override
    public boolean exists(String authorityCode) {
        return authorities.findAuthority(authorityCode).isPresent();
    }

    @Override
    public List<AuthorityEntry> all() {
        return authorities.listAuthorities().stream()
            .map(a -> new AuthorityEntry(a.code(), a.name(), a.mailbox()))
            .toList();
    }
}
