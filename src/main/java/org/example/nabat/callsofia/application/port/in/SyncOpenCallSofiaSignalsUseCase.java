package org.example.nabat.callsofia.application.port.in;

/**
 * Reads Call Sofia's public map once and records what is on it, and what is no longer.
 */
public interface SyncOpenCallSofiaSignalsUseCase {

    SyncSummary sync();

    /**
     * @param onMap    reports on the map now
     * @param newlySeen of those, reports nabat had not seen before
     * @param agedOut  reports that left the map because they passed 30 days
     * @param noLongerProcessing reports that left it inside the 30 days — their status changed
     */
    record SyncSummary(int onMap, int newlySeen, int agedOut, int noLongerProcessing) {
    }
}
