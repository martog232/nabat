package org.example.nabat.callsofia.application.port.out;

import org.example.nabat.shared.domain.Location;

import java.util.Optional;

/**
 * Which Sofia district a point is in. Declared here, implemented by {@code jurisdiction},
 * which holds the boundaries — the module that needs the answer declares the port.
 */
public interface DistrictLocatorPort {

    /** The code from {@code districts}, or empty outside the city. */
    Optional<String> districtCodeAt(Location location);
}
