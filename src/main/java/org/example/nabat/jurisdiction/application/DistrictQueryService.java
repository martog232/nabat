package org.example.nabat.jurisdiction.application;

import org.example.nabat.jurisdiction.application.port.in.ListDistrictsUseCase;
import org.example.nabat.jurisdiction.application.port.out.DistrictRepository;
import org.example.nabat.jurisdiction.domain.District;
import org.example.nabat.shared.UseCase;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@UseCase
public class DistrictQueryService implements ListDistrictsUseCase {

    private final DistrictRepository districts;

    public DistrictQueryService(DistrictRepository districts) {
        this.districts = districts;
    }

    @Override
    @Transactional(readOnly = true)
    public List<District> listDistricts() {
        return districts.findAllOrderedByName();
    }
}
