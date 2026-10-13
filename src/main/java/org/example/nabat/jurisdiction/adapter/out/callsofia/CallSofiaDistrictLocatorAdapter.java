package org.example.nabat.jurisdiction.adapter.out.callsofia;

import org.example.nabat.callsofia.application.port.out.DistrictLocatorPort;
import org.example.nabat.jurisdiction.application.port.out.DistrictRepository;
import org.example.nabat.jurisdiction.domain.District;
import org.example.nabat.shared.domain.Location;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Places Call Sofia's live reports in a district, which its map does not give. Same
 * direction as {@code DistrictLookupAdapter} for {@code incident}: the module with the
 * boundaries implements the port of the module that needs them.
 */
@Component
public class CallSofiaDistrictLocatorAdapter implements DistrictLocatorPort {

    private final DistrictRepository districts;

    public CallSofiaDistrictLocatorAdapter(DistrictRepository districts) {
        this.districts = districts;
    }

    @Override
    public Optional<String> districtCodeAt(Location location) {
        return districts.findCovering(location).map(District::code);
    }
}
