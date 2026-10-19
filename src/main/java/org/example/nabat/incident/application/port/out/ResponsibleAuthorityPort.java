package org.example.nabat.incident.application.port.out;

/**
 * Who answers for a new report: its district, or the city-wide institution its category
 * belongs to.
 *
 * <p>Declared here, beside {@link DistrictLookupPort}, and implemented by
 * {@code jurisdiction}, which holds the institutions and which categories go to them.
 */
public interface ResponsibleAuthorityPort {

    /** @return an authority code — the district's own when no institution takes the category */
    String authorityFor(String districtCode, int categoryCode);
}
