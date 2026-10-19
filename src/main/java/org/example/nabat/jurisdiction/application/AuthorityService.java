package org.example.nabat.jurisdiction.application;

import org.example.nabat.jurisdiction.application.port.in.AssignAuthorityUseCase;
import org.example.nabat.jurisdiction.application.port.in.ListAuthoritiesUseCase;
import org.example.nabat.jurisdiction.application.port.out.DistrictRepository;
import org.example.nabat.jurisdiction.application.port.out.InstitutionRepository;
import org.example.nabat.jurisdiction.domain.Authority;
import org.example.nabat.jurisdiction.domain.Institution;
import org.example.nabat.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Districts and institutions as one list of authorities, and which of them a report goes to.
 *
 * <p>The two never share a code — {@code AuthorityRoutingIntegrationTest} holds that — so a
 * code is looked up as a district first and an institution second without ambiguity.
 */
@UseCase
public class AuthorityService implements ListAuthoritiesUseCase, AssignAuthorityUseCase {

    private final DistrictRepository districts;
    private final InstitutionRepository institutions;

    public AuthorityService(DistrictRepository districts, InstitutionRepository institutions) {
        this.districts = districts;
        this.institutions = institutions;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Authority> listAuthorities() {
        return Stream.concat(
                districts.findAllOrderedByName().stream().map(Authority::of),
                institutions.findAllOrderedByName().stream().map(Authority::of))
            .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Authority> findAuthority(String code) {
        if (code == null) {
            return Optional.empty();
        }
        return districts.findByCode(code).map(Authority::of)
            .or(() -> institutions.findByCode(code).map(Authority::of));
    }

    @Override
    @Transactional(readOnly = true)
    public String responsibleFor(String districtCode, int categoryCode) {
        return institutions.findForCategory(categoryCode)
            .map(Institution::code)
            .orElse(districtCode);
    }
}
