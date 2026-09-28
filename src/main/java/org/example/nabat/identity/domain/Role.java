package org.example.nabat.identity.domain;

/**
 * What a user is allowed to do.
 *
 * <p>Separate roles, because the privileges the platform needs fall into two unrelated groups
 * and one role for both was the wrong shape. Cleaning up other people's alerts is frequent,
 * low-risk work — a false alarm on a safety map has to be closable by someone other than the
 * person who reported it. Administering accounts is rare and irreversible. Bundling them into
 * a single {@code ADMIN} meant whoever triages content also holds the power to disable
 * accounts and hand out roles.
 *
 * <p><b>Capabilities, not a rank.</b> Ask {@link #canModerateContent()}, never
 * {@code role == ADMIN} or {@code role.ordinal() >= …}. A numeric rank reads naturally right
 * up to the first role that does not fit the line — an auditor who may read everything and
 * moderate nothing, a responder who may confirm alerts but not close them — and then every
 * {@code >=} comparison in the codebase is quietly wrong. Named questions keep each new role
 * a decision about which capabilities it answers yes to.
 *
 * <p>These names are also the authority for the {@code CHECK} constraint on
 * {@code users.role} (V12, widened in V20). Adding a constant here means adding it there.
 */
public enum Role {

    /** Report alerts, vote, subscribe, manage your own profile and your own alerts. */
    USER,

    /** Everything a user can do, plus closing and listing alerts that belong to others. */
    MODERATOR,

    /** Everything a moderator can do, plus changing roles and disabling accounts. */
    ADMIN,

    /**
     * Acts for one district administration: acknowledges, starts work on, resolves and rejects
     * the reports in that district, and no other. Which district is on the account
     * ({@link User#districtCode()}), not on the role — it is the same role for all 24.
     *
     * <p>Not a moderator. An official is trusted with their own territory, and nothing about
     * that makes them fit to close a report in the next district, or anyone else's content.
     */
    DISTRICT_OFFICIAL;

    /**
     * May act on content someone else created — resolving another user's alert, or listing
     * alerts regardless of who reported them.
     */
    public boolean canModerateContent() {
        return this == MODERATOR || this == ADMIN;
    }

    /**
     * May change what other accounts are allowed to do, or switch them off entirely.
     *
     * <p>Deliberately narrower than {@link #canModerateContent()}: this is the capability
     * that can lock every other user — including every other admin — out of the platform.
     */
    public boolean canAdministerUsers() {
        return this == ADMIN;
    }

    /**
     * Speaks for a district: may move that district's reports through their lifecycle.
     *
     * <p>Only half the question. The other half — is this report in <em>their</em> district —
     * needs the account's {@link User#districtCode()}; see {@link User#actsForDistrict}.
     */
    public boolean canActForDistrict() {
        return this == DISTRICT_OFFICIAL;
    }
}
