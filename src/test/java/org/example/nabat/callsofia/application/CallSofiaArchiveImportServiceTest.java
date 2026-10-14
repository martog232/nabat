package org.example.nabat.callsofia.application;

import org.example.nabat.callsofia.application.port.in.ImportCallSofiaArchiveUseCase.ImportSummary;
import org.example.nabat.callsofia.application.port.out.CallSofiaArchive;
import org.example.nabat.callsofia.application.port.out.CallSofiaArchiveStore;
import org.example.nabat.callsofia.domain.CallSofiaCategory;
import org.example.nabat.callsofia.domain.CallSofiaSignal;
import org.example.nabat.callsofia.domain.CallSofiaStatus;
import org.example.nabat.callsofia.domain.CallSofiaStatusChange;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class CallSofiaArchiveImportServiceTest {

    private static final Instant T = Instant.parse("2024-05-01T08:00:00Z");

    private final RecordingStore store = new RecordingStore();

    @Test
    void writesInBatchesAndCountsEveryRow() {
        int n = 2 * CallSofiaArchiveImportService.BATCH_SIZE + 1;
        FakeArchive archive = new FakeArchive(signals(1, n), List.of());

        ImportSummary summary = new CallSofiaArchiveImportService(archive, store).importArchive();

        assertThat(store.signalBatchSizes).containsExactly(1000, 1000, 1);
        assertThat(summary.signals()).isEqualTo(n);
    }

    /** Its report is not in the archive, so its foreign key would fail the whole batch. */
    @Test
    void leavesOutHistoryForReportsTheArchiveDoesNotHave() {
        FakeArchive archive = new FakeArchive(signals(1, 2), List.of(
            step(1, 0), step(3, 0), step(2, 0), step(3, 1)));

        ImportSummary summary = new CallSofiaArchiveImportService(archive, store).importArchive();

        assertThat(store.statusChanges).extracting(CallSofiaStatusChange::signalId).containsExactly(1L, 2L);
        assertThat(summary.statusChanges()).isEqualTo(2);
        assertThat(summary.orphanedStatusChanges()).isEqualTo(2);
    }

    @Test
    void closesBothStreams() {
        FakeArchive archive = new FakeArchive(signals(1, 1), List.of(step(1, 0)));

        new CallSofiaArchiveImportService(archive, store).importArchive();

        assertThat(archive.signalsClosed).isTrue();
        assertThat(archive.statusChangesClosed).isTrue();
    }

    private static List<CallSofiaSignal> signals(long from, long to) {
        return LongStream.rangeClosed(from, to)
            .mapToObj(id -> new CallSofiaSignal(id, 3056, "LOZENETS", T, null, "Дупка"))
            .toList();
    }

    private static CallSofiaStatusChange step(long signalId, int position) {
        return new CallSofiaStatusChange(signalId, position, T, CallSofiaStatus.PROCESSING);
    }

    private static final class FakeArchive implements CallSofiaArchive {
        private final List<CallSofiaSignal> signals;
        private final List<CallSofiaStatusChange> statusChanges;
        final AtomicBoolean signalsClosed = new AtomicBoolean();
        final AtomicBoolean statusChangesClosed = new AtomicBoolean();

        FakeArchive(List<CallSofiaSignal> signals, List<CallSofiaStatusChange> statusChanges) {
            this.signals = signals;
            this.statusChanges = statusChanges;
        }

        @Override
        public List<CallSofiaCategory> categories() {
            return List.of(new CallSofiaCategory(3056, "Дупки"));
        }

        @Override
        public Stream<CallSofiaSignal> signals() {
            return signals.stream().onClose(() -> signalsClosed.set(true));
        }

        @Override
        public Stream<CallSofiaStatusChange> statusChanges() {
            return statusChanges.stream().onClose(() -> statusChangesClosed.set(true));
        }
    }

    private static final class RecordingStore implements CallSofiaArchiveStore {
        final List<Integer> signalBatchSizes = new ArrayList<>();
        final List<CallSofiaStatusChange> statusChanges = new ArrayList<>();

        @Override
        public void saveCategories(List<CallSofiaCategory> categories) {
        }

        @Override
        public void saveSignals(List<CallSofiaSignal> signals) {
            signalBatchSizes.add(signals.size());
        }

        @Override
        public void saveStatusChanges(List<CallSofiaStatusChange> changes) {
            statusChanges.addAll(changes);
        }

        @Override
        public void refreshOutcomes() {
        }
    }
}
