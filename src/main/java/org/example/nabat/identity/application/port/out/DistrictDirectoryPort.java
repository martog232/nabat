package org.example.nabat.identity.application.port.out;

import java.util.List;

/**
 * The districts an official can be appointed to.
 *
 * <p>Declared here and implemented by {@code jurisdiction}, which holds the districts.
 * {@code identity} cannot ask that module directly: {@code jurisdiction} already depends on
 * {@code identity} to name the reporter of a report it mails, and the dependency the other
 * way would close a cycle.
 */
public interface DistrictDirectoryPort {

    boolean exists(String districtCode);

    /** Every district, with the mailbox its administration receives reports at. */
    List<DistrictEntry> all();

    /**
     * @param mailbox where the district's reports are mailed, and so the address its
     *                official account signs in with
     */
    record DistrictEntry(String code, String name, String mailbox) {
    }
}
