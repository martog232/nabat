package org.example.nabat.incident.domain;

/**
 * Who closed a report — resolved or rejected it.
 *
 * <p>Kept because the answers are not worth the same. "The authority says it is fixed" is a
 * claim by the body responsible for fixing it; "the person who reported it says so" is a
 * citizen's word about their own report; a moderator closing one is housekeeping. A client
 * showing all three as the same "resolved" would be hiding the difference that matters most
 * to whoever reads it.
 *
 * <p>Persisted by name, pinned by {@code alerts_closed_by_check} (V20, renamed in V28).
 */
public enum ClosedBy {
    /**
     * An official of the authority that answers for the report: its district, or the
     * institution its category belongs to. Was {@code DISTRICT} until V28, when districts
     * stopped being the only authority. Events stored before that are rewritten by V28.
     */
    AUTHORITY,
    /** The person who made the report. */
    REPORTER,
    /** A moderator, acting on someone else's content. */
    MODERATOR
}
