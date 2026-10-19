package org.example.nabat.identity.application.port.in;

import java.util.List;

/**
 * One official account for every authority that has none.
 *
 * <p>A report is mailed to the authority that answers for it, but acknowledging it, starting
 * work on it and resolving it in nabat takes an account that acts for that authority.
 * Appointing one per district and per institution by hand, each through a registration and
 * then the admin endpoint, is not something anyone does twice.
 */
public interface ProvisionOfficialsUseCase {

    /**
     * Creates the account of each authority that has none. It signs in with the authority's
     * mailbox and {@code password}.
     *
     * <p>Idempotent: an account that already exists is never changed, neither its password
     * nor its role. An address taken by an account that is not that authority's official is
     * reported as a conflict and left alone, because promoting someone's account as a side
     * effect of startup is a grant nobody made.
     *
     * @throws IllegalArgumentException when {@code password} breaks the password policy
     */
    Result provision(String password);

    /** @param conflicts codes of the authorities whose mailbox belongs to another account */
    record Result(int created, int alreadyPresent, List<String> conflicts) {
    }
}
