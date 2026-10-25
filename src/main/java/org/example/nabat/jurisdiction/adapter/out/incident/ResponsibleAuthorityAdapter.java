package org.example.nabat.jurisdiction.adapter.out.incident;

import org.example.nabat.incident.application.port.out.ResponsibleAuthorityPort;
import org.example.nabat.jurisdiction.application.port.in.AssignAuthorityUseCase;
import org.example.nabat.jurisdiction.application.port.in.ListAuthoritiesUseCase;
import org.example.nabat.jurisdiction.domain.Authority;
import org.springframework.stereotype.Component;

import java.util.Optional;

/** Answers {@code incident}'s questions about who answers for a report from the routing here. */
@Component
public class ResponsibleAuthorityAdapter implements ResponsibleAuthorityPort {

    private final AssignAuthorityUseCase assign;
    private final ListAuthoritiesUseCase authorities;

    public ResponsibleAuthorityAdapter(AssignAuthorityUseCase assign, ListAuthoritiesUseCase authorities) {
        this.assign = assign;
        this.authorities = authorities;
    }

    @Override
    public String authorityFor(String districtCode, int categoryCode) {
        return assign.responsibleFor(districtCode, categoryCode);
    }

    @Override
    public Optional<String> nameOf(String authorityCode) {
        return authorities.findAuthority(authorityCode).map(Authority::name);
    }
}
