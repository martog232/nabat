package org.example.nabat.identity.application.port.in;

import java.util.List;

/**
 * One official account for every district that has none.
 *
 * <p>A report is mailed to its district, but acknowledging it, starting work on it and
 * resolving it in nabat takes an account that acts for that district. Appointing 24 of them
 * by hand, each through a registration and then the admin endpoint, is not something anyone
 * does twice.
 */
public interface ProvisionDistrictOfficialsUseCase {

    /**
     * Creates the account of each district that has none. It signs in with the district's
     * mailbox and {@code password}.
     *
     * <p>Idempotent: an account that already exists is never changed, neither its password
     * nor its role. An address taken by an account that is not that district's official is
     * reported as a conflict and left alone, because promoting someone's account as a side
     * effect of startup is a grant nobody made.
     *
     * @throws IllegalArgumentException when {@code password} breaks the password policy
     */
    Result provision(String password);

    /** @param conflicts codes of the districts whose mailbox belongs to another account */
    record Result(int created, int alreadyPresent, List<String> conflicts) {
    }
}
