package org.example.nabat.jurisdiction.application.port.out;

import org.example.nabat.jurisdiction.domain.Institution;

import java.util.List;
import java.util.Optional;

public interface InstitutionRepository {

    List<Institution> findAllOrderedByName();

    Optional<Institution> findByCode(String code);

    /** The institution a category of report goes to, or empty when it goes to the district. */
    Optional<Institution> findForCategory(int categoryCode);
}
