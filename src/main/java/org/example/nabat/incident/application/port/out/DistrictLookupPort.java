package org.example.nabat.incident.application.port.out;

import org.example.nabat.shared.domain.Location;

import java.util.Optional;

/**
 * Which district of the city a place belongs to.
 *
 * <p>Declared here because a report cannot be accepted without the answer, and implemented
 * by {@code jurisdiction}, which holds the boundaries. This module does not know that the
 * answer comes from polygons — only that some places are in the city and some are not.
 */
public interface DistrictLookupPort {

    /** @return the district containing {@code location}, or empty when it is outside the city */
    Optional<District> districtAt(Location location);

    /** As much of a district as this module needs: a stable code to store, a name to show. */
    record District(String code, String name) {
    }
}
