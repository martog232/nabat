package org.example.nabat.jurisdiction.adapter.out.identity;

import org.example.nabat.identity.application.port.out.AuthorityDirectoryPort;
import org.example.nabat.jurisdiction.application.port.out.DistrictRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/** Answers {@code identity}'s questions about districts from the districts this module holds. */
@Component
public class AuthorityDirectoryAdapter implements AuthorityDirectoryPort {

    private final DistrictRepository districts;

    public AuthorityDirectoryAdapter(DistrictRepository districts) {
        this.districts = districts;
    }

    @Override
    public boolean exists(String districtCode) {
        return districts.findByCode(districtCode).isPresent();
    }

    @Override
    public List<AuthorityEntry> all() {
        return districts.findAllOrderedByName().stream()
            .map(d -> new AuthorityEntry(d.code(), d.name(), d.signalEmail()))
            .toList();
    }
}
