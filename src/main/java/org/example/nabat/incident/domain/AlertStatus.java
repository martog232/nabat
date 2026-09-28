package org.example.nabat.incident.domain;

/**
 * Where a signal is on its way from a citizen to a fix.
 *
 * <p>The open states are declared in the order a signal moves through them: accepted by
 * nabat, delivered to the institution responsible, acknowledged by it, being worked on.
 * A signal only ever moves <em>forward</em> through them, but it may skip — an institution
 * that starts work on something it sees on its own page has not waited for the email.
 *
 * <p>{@code RESOLVED} and {@code REJECTED} are final and can be reached from any open
 * state. A pothole that gets filled does not wait for the paperwork to catch up, and an
 * institution that never answers must not be able to keep a signal open forever by
 * saying nothing.
 */
public enum AlertStatus {
    /** Accepted by nabat, not yet delivered to anyone responsible. */
    SUBMITTED,
    /** Delivered to the responsible institution. */
    SENT,
    /** The institution has confirmed it received the signal. */
    ACKNOWLEDGED,
    /** The institution reports that work has started. */
    IN_PROGRESS,
    /** Fixed. */
    RESOLVED,
    /** Closed without a fix — a duplicate, not a real problem, not anyone's to fix. */
    REJECTED;

    /** True while the signal still describes a problem that is out there. */
    public boolean isOpen() {
        return this != RESOLVED && this != REJECTED;
    }

    /**
     * Whether a signal in this state may move to {@code target}.
     *
     * <p>Relies on declaration order for the open states, which is why the order above
     * is part of the contract and not a matter of taste. A new open state goes where it
     * happens in the life of a signal.
     */
    public boolean canMoveTo(AlertStatus target) {
        if (!isOpen()) {
            return false;
        }
        return !target.isOpen() || target.ordinal() > ordinal();
    }
}
