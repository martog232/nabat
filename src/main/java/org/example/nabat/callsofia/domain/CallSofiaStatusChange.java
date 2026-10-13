package org.example.nabat.callsofia.domain;

import java.time.Instant;
import java.util.Objects;

/**
 * One step in a Call Sofia report's history.
 *
 * @param position the step's place in the report's history, from 0. This, not
 *                 {@code changedAt}, is the order: two steps often share a timestamp.
 */
public record CallSofiaStatusChange(
    long signalId,
    int position,
    Instant changedAt,
    CallSofiaStatus status
) {
    public CallSofiaStatusChange {
        Objects.requireNonNull(changedAt, "changedAt");
        Objects.requireNonNull(status, "status");
        if (position < 0) {
            throw new IllegalArgumentException("position must not be negative: " + position);
        }
    }
}
