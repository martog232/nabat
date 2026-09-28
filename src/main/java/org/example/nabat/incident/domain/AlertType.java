package org.example.nabat.incident.domain;

/**
 * What is wrong with the city, as the person reporting it can tell from the street.
 *
 * <p>These are categories a citizen can choose without knowing whose job the fix is — a
 * manhole is a manhole whether it belongs to the water utility or the district heating
 * company. Deciding who is responsible is routing, and it takes the type and the location
 * together; keeping that knowledge out of this list is what lets the routing change
 * without renaming anything a client has already sent.
 *
 * <p>The constants are persisted by name and pinned by a {@code CHECK} constraint in
 * {@code V15__civic_signal_types_and_lifecycle.sql}; adding one needs a migration too.
 */
public enum AlertType {
    /** A hole in the road or pavement. */
    POTHOLE,
    /** A missing, broken or sunken manhole cover. */
    MANHOLE,
    /** Uncollected rubbish, overflowing bins, an illegal dump. */
    WASTE,
    /** Air, water or noise pollution. */
    POLLUTION,
    /** A street light that is out or damaged. */
    STREET_LIGHT,
    /** A vehicle blocking a pavement, crossing or access. */
    ILLEGAL_PARKING,
    OTHER
}
