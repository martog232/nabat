package org.example.nabat.jurisdiction.application.port.in;

import org.example.nabat.jurisdiction.domain.Authority;

import java.util.List;
import java.util.Optional;

/** The authorities reports go to: the 24 districts, then the city-wide institutions. */
public interface ListAuthoritiesUseCase {

    /** Districts first, then institutions, each ordered by name. */
    List<Authority> listAuthorities();

    Optional<Authority> findAuthority(String code);
}
