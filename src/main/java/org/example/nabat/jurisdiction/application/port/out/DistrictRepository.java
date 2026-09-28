package org.example.nabat.jurisdiction.application.port.out;

import org.example.nabat.jurisdiction.domain.District;
import org.example.nabat.shared.domain.Location;

import java.util.List;
import java.util.Optional;

public interface DistrictRepository {

    List<District> findAllOrderedByName();

    /**
     * The district whose boundary covers {@code location}, boundary included.
     *
     * <p>Covers, not contains: a point exactly on the line between two districts is in
     * both, and "contains" would put it in neither — a report on a boundary street refused
     * as outside the city. The first by code is returned, so the answer is stable.
     */
    Optional<District> findCovering(Location location);
}
