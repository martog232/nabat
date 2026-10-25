package org.example.nabat.incident.application.port.out;

import java.util.Optional;

/**
 * Who answers for a report: its district, or the city-wide institution its category belongs
 * to — and, for a forward, whether another authority exists and what it is called.
 *
 * <p>Declared here, beside {@link DistrictLookupPort}, and implemented by
 * {@code jurisdiction}, which holds the districts, the institutions and which categories go
 * to them.
 */
public interface ResponsibleAuthorityPort {

    /** @return an authority code — the district's own when no institution takes the category */
    String authorityFor(String districtCode, int categoryCode);

    /** The authority's name, or empty when there is no authority with that code. */
    Optional<String> nameOf(String authorityCode);
}
