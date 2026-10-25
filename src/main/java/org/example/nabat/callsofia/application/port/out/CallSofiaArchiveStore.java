package org.example.nabat.callsofia.application.port.out;

import org.example.nabat.callsofia.domain.CallSofiaCategory;
import org.example.nabat.callsofia.domain.CallSofiaSignal;
import org.example.nabat.callsofia.domain.CallSofiaStatusChange;

import java.util.List;

/**
 * nabat's copy of the archive. Every method writes its batch in one transaction and may
 * be called again with rows it has already seen.
 */
public interface CallSofiaArchiveStore {

    /** Inserts new categories and renames existing ones. */
    void saveCategories(List<CallSofiaCategory> categories);

    /** Inserts new reports and overwrites existing ones with the archive's version. */
    void saveSignals(List<CallSofiaSignal> signals);

    /** Inserts steps not yet recorded; a step already at its position is left as it is. */
    void saveStatusChanges(List<CallSofiaStatusChange> changes);

    /** Recomputes each report's outcome (V26) from what is now stored. */
    void refreshOutcomes();
}
