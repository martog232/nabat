package org.example.nabat.jurisdiction.adapter.out.incident;

import org.example.nabat.incident.application.port.out.DistrictLookupPort;
import org.example.nabat.jurisdiction.application.port.out.DistrictRepository;
import org.example.nabat.shared.domain.Location;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Answers {@code incident}'s question — which district is this report in — from the
 * boundaries this module holds.
 *
 * <p>The dependency points from here to {@code incident}, not the other way: the module
 * that needs the answer declares the port, the one with the data implements it.
 */
@Component
public class DistrictLookupAdapter implements DistrictLookupPort {

    private final DistrictRepository districts;

    public DistrictLookupAdapter(DistrictRepository districts) {
        this.districts = districts;
    }

    @Override
    public Optional<District> districtAt(Location location) {
        return districts.findCovering(location)
            .map(found -> new District(found.code(), found.name()));
    }
}
