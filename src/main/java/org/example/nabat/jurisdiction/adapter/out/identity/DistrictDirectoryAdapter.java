package org.example.nabat.jurisdiction.adapter.out.identity;

import org.example.nabat.identity.application.port.out.DistrictDirectoryPort;
import org.example.nabat.jurisdiction.application.port.out.DistrictRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/** Answers {@code identity}'s questions about districts from the districts this module holds. */
@Component
public class DistrictDirectoryAdapter implements DistrictDirectoryPort {

    private final DistrictRepository districts;

    public DistrictDirectoryAdapter(DistrictRepository districts) {
        this.districts = districts;
    }

    @Override
    public boolean exists(String districtCode) {
        return districts.findByCode(districtCode).isPresent();
    }

    @Override
    public List<DistrictEntry> all() {
        return districts.findAllOrderedByName().stream()
            .map(d -> new DistrictEntry(d.code(), d.name(), d.signalEmail()))
            .toList();
    }
}
