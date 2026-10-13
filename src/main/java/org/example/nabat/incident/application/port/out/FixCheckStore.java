package org.example.nabat.incident.application.port.out;

import org.example.nabat.incident.domain.FixCheckTally;
import org.example.nabat.incident.domain.FixVerdict;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The answers to "is it really fixed?". Every write returns the report's tally as counted
 * after it, and keeps the tally on the report in step — in the same transaction, under a
 * lock on the report, so two people answering at once cannot both count from the same
 * starting point.
 */
public interface FixCheckStore {

    FixCheckTally record(UUID alertId, UUID userId, FixVerdict verdict, boolean byReporter, Instant at);

    FixCheckTally withdraw(UUID alertId, UUID userId);

    Optional<FixVerdict> verdictOf(UUID alertId, UUID userId);
}
