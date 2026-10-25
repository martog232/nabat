package org.example.nabat.jurisdiction.application.port.in;

import org.example.nabat.jurisdiction.domain.District;

import java.util.List;

public interface ListDistrictsUseCase {

    /** Every district, ordered by name — the list a client shows next to a report. */
    List<District> listDistricts();
}
