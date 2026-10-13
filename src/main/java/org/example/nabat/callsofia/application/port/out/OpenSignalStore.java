package org.example.nabat.callsofia.application.port.out;

import org.example.nabat.callsofia.domain.CallSofiaOpenSignal;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What nabat has seen on Call Sofia's map.
 */
public interface OpenSignalStore {

    /** Of these ids, the ones already recorded. */
    Set<Long> knownIds(Collection<Long> ids);

    /**
     * Records one reading of the map, in one transaction: every report on it is written
     * with {@code seenAt}, and every report that was on the map before and is not now is
     * marked as gone.
     *
     * @param districtCodes districts for the reports not seen before; reports already
     *                      recorded keep the district they have
     * @param agedBefore    a report gone from the map that was filed before this instant
     *                      aged out; one filed after it left processing
     */
    Departures recordReading(
        List<CallSofiaOpenSignal> onMap,
        Map<Long, String> districtCodes,
        Instant seenAt,
        Instant agedBefore);

    record Departures(int agedOut, int noLongerProcessing) {
    }
}
