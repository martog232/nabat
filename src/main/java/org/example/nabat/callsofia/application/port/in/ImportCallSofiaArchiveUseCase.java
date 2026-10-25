package org.example.nabat.callsofia.application.port.in;

/**
 * Brings nabat's copy of the Call Sofia archive up to date with the archive.
 *
 * <p>Safe to repeat: a report already imported is updated in place, a step already in its
 * history is left alone. Running it twice on the same archive changes nothing.
 */
public interface ImportCallSofiaArchiveUseCase {

    ImportSummary importArchive();

    /**
     * @param orphanedStatusChanges history rows for reports the archive does not contain,
     *                              which have nothing to attach to and are left out
     */
    record ImportSummary(int categories, long signals, long statusChanges, long orphanedStatusChanges) {
    }
}
