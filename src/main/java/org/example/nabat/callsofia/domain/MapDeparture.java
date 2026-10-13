package org.example.nabat.callsofia.domain;

/**
 * Why a report is no longer on Call Sofia's map. Decided when nabat notices it gone, from
 * the one thing that tells the two apart: when the report was filed.
 *
 * <p>Persisted by name and pinned by a {@code CHECK} in {@code V22}.
 */
public enum MapDeparture {
    /** Filed more than 30 days ago. The map stopped showing it; its status is not known. */
    AGED_OUT,
    /**
     * Still inside the 30 days, so it left because it is no longer in processing —
     * answered, closed or rejected. The map does not say which.
     */
    NO_LONGER_PROCESSING
}
