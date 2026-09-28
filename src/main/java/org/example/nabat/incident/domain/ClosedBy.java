package org.example.nabat.incident.domain;

/**
 * Who closed a report — resolved or rejected it.
 *
 * <p>Kept because the answers are not worth the same. "The district says it is fixed" is a
 * claim by the body responsible for fixing it; "the person who reported it says so" is a
 * citizen's word about their own report; a moderator closing one is housekeeping. A client
 * showing all three as the same "resolved" would be hiding the difference that matters most
 * to whoever reads it.
 *
 * <p>Persisted by name, pinned by {@code alerts_closed_by_check} (V20).
 */
public enum ClosedBy {
    /** An official of the report's own district. */
    DISTRICT,
    /** The person who made the report. */
    REPORTER,
    /** A moderator, acting on someone else's content. */
    MODERATOR
}
