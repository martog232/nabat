package org.example.nabat.callsofia.application;

import org.example.nabat.callsofia.application.port.in.ImportCallSofiaArchiveUseCase;
import org.example.nabat.callsofia.application.port.out.CallSofiaArchive;
import org.example.nabat.callsofia.application.port.out.CallSofiaArchiveStore;
import org.example.nabat.callsofia.domain.CallSofiaCategory;
import org.example.nabat.callsofia.domain.CallSofiaSignal;
import org.example.nabat.callsofia.domain.CallSofiaStatusChange;
import org.example.nabat.shared.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Streams the archive into the store in batches.
 *
 * <p>Not {@code @Transactional}, on purpose. The archive is three quarters of a million
 * reports and about three million steps; one transaction would hold all of it until the
 * end and lose all of it to a dropped connection at the last row. Each batch commits on
 * its own instead, and since every write is an upsert, a run that stops halfway is
 * finished by running it again.
 */
@UseCase
public class CallSofiaArchiveImportService implements ImportCallSofiaArchiveUseCase {

    private static final Logger log = LoggerFactory.getLogger(CallSofiaArchiveImportService.class);

    static final int BATCH_SIZE = 1_000;
    private static final long PROGRESS_EVERY = 100_000;

    private final CallSofiaArchive archive;
    private final CallSofiaArchiveStore store;

    public CallSofiaArchiveImportService(CallSofiaArchive archive, CallSofiaArchiveStore store) {
        this.archive = archive;
        this.store = store;
    }

    @Override
    public ImportSummary importArchive() {
        List<CallSofiaCategory> categories = archive.categories();
        store.saveCategories(categories);

        // The history names reports by id, and a step whose report is not in the archive
        // would fail its foreign key and take the whole batch with it. The ids are small
        // and dense, so a bit per id is about 100 KB, where a Set<Long> would be tens of MB.
        BitSet imported = new BitSet();
        long signals;
        try (Stream<CallSofiaSignal> stream = archive.signals()) {
            signals = inBatches(stream.iterator(), "reports", batch -> {
                store.saveSignals(batch);
                batch.forEach(signal -> imported.set(Math.toIntExact(signal.id())));
            });
        }

        long[] orphaned = {0};
        long statusChanges;
        try (Stream<CallSofiaStatusChange> stream = archive.statusChanges()) {
            Stream<CallSofiaStatusChange> attached = stream.filter(change -> {
                boolean known = imported.get(Math.toIntExact(change.signalId()));
                if (!known) {
                    orphaned[0]++;
                }
                return known;
            });
            statusChanges = inBatches(attached.iterator(), "status changes", store::saveStatusChanges);
        }

        // The district comparison reads outcomes worked out from all of the above, once.
        store.refreshOutcomes();

        ImportSummary summary = new ImportSummary(categories.size(), signals, statusChanges, orphaned[0]);
        log.info("Call Sofia archive imported: {}", summary);
        return summary;
    }

    private static <T> long inBatches(Iterator<T> rows, String what, Consumer<List<T>> save) {
        List<T> batch = new ArrayList<>(BATCH_SIZE);
        long total = 0;
        while (rows.hasNext()) {
            batch.add(rows.next());
            if (batch.size() == BATCH_SIZE) {
                save.accept(batch);
                total += batch.size();
                batch = new ArrayList<>(BATCH_SIZE);
                if (total % PROGRESS_EVERY == 0) {
                    log.info("Call Sofia archive: {} {} so far", total, what);
                }
            }
        }
        if (!batch.isEmpty()) {
            save.accept(batch);
            total += batch.size();
        }
        return total;
    }
}
