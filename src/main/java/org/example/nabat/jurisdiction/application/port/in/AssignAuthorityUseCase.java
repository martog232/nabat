package org.example.nabat.jurisdiction.application.port.in;

/** Who answers for a new report. */
public interface AssignAuthorityUseCase {

    /**
     * The institution the category goes to, if it goes to one, and otherwise the district.
     *
     * <p>The category decides only when it decides alone: a bus stop is the Centre for Urban
     * Mobility's wherever it is. Everything whose owner depends on the street — most of the
     * list — is the district's, which knows its streets (V29).
     *
     * @return an authority code: an institution's, or {@code districtCode}
     */
    String responsibleFor(String districtCode, int categoryCode);
}
