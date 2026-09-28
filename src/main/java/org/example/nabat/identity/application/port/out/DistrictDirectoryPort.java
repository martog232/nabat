package org.example.nabat.identity.application.port.out;

/**
 * Whether a district exists, so an official is never appointed to one that does not.
 *
 * <p>Declared here and implemented by {@code jurisdiction}, which holds the districts.
 * {@code identity} cannot ask that module directly: {@code jurisdiction} already depends on
 * {@code identity} to name the reporter of a report it mails, and the dependency the other
 * way would close a cycle.
 */
public interface DistrictDirectoryPort {

    boolean exists(String districtCode);
}
