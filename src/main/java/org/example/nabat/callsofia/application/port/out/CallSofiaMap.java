package org.example.nabat.callsofia.application.port.out;

import org.example.nabat.callsofia.domain.CallSofiaOpenSignal;

import java.util.List;

/**
 * Call Sofia's public map of open reports.
 *
 * <p>There is no API behind this. The map's own page asks the site for its data, and
 * whatever implements this port asks the same way — so it can stop working on any day
 * the site changes, and must say so loudly rather than return less.
 */
public interface CallSofiaMap {

    /**
     * Every report the map shows for the last 30 days.
     *
     * @throws RuntimeException if the map could not be read whole. Never a partial list:
     *                          a report missing from the answer is taken to have left the map.
     */
    List<CallSofiaOpenSignal> openSignals();
}
