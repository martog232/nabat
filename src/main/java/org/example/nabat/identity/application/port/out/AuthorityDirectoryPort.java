package org.example.nabat.identity.application.port.out;

import java.util.List;

/**
 * The authorities an official can be appointed to: the districts, and the city-wide
 * institutions reports go to by category.
 *
 * <p>Declared here and implemented by {@code jurisdiction}, which holds them. {@code identity}
 * cannot ask that module directly: {@code jurisdiction} already depends on {@code identity} to
 * name the reporter of a report it mails, and the dependency the other way would close a cycle.
 */
public interface AuthorityDirectoryPort {

    boolean exists(String authorityCode);

    /** Every authority, with the mailbox it receives reports at. */
    List<AuthorityEntry> all();

    /**
     * @param mailbox where the authority's reports are mailed, and so the address its official
     *                account signs in with
     */
    record AuthorityEntry(String code, String name, String mailbox) {
    }
}
