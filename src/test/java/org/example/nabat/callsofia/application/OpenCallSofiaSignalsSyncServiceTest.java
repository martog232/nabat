package org.example.nabat.callsofia.application;

import org.example.nabat.callsofia.application.port.in.SyncOpenCallSofiaSignalsUseCase.SyncSummary;
import org.example.nabat.callsofia.application.port.out.DistrictLocatorPort;
import org.example.nabat.callsofia.application.port.out.OpenSignalStore;
import org.example.nabat.callsofia.domain.CallSofiaOpenSignal;
import org.example.nabat.callsofia.domain.CallSofiaStatus;
import org.example.nabat.shared.domain.Location;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenCallSofiaSignalsSyncServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-13T14:00:00.123456789Z");

    private final RecordingStore store = new RecordingStore();
    private final List<Location> lookedUp = new ArrayList<>();
    private final DistrictLocatorPort districts = location -> {
        lookedUp.add(location);
        return Optional.of("MLADOST");
    };

    @Test
    void looksUpADistrictOnlyForReportsNotSeenBefore() {
        store.known = Set.of(1L);

        SyncSummary summary = service(List.of(signal(1), signal(2), signal(3))).sync();

        assertThat(lookedUp).hasSize(2);
        assertThat(store.districtCodes).containsOnlyKeys(2L, 3L);
        assertThat(summary.onMap()).isEqualTo(3);
        assertThat(summary.newlySeen()).isEqualTo(2);
    }

    /**
     * Thirty days back, then a quarter of an hour towards now: a report close to the edge
     * counts as aged out, not as answered.
     */
    @Test
    void passesTheAgeLineWithSlackTowardsAgedOut() {
        service(List.of(signal(1))).sync();

        assertThat(store.seenAt).isEqualTo(Instant.parse("2026-10-13T14:00:00.123456Z"));
        assertThat(store.agedBefore).isEqualTo(Instant.parse("2026-09-13T14:15:00.123456Z"));
    }

    /** Recorded, it would mark every report in the city as gone in one reading. */
    @Test
    void refusesToRecordAnEmptyMap() {
        assertThatThrownBy(() -> service(List.of()).sync()).isInstanceOf(IllegalStateException.class);
        assertThat(store.recorded).isFalse();
    }

    private OpenCallSofiaSignalsSyncService service(List<CallSofiaOpenSignal> onMap) {
        return new OpenCallSofiaSignalsSyncService(() -> onMap, store, districts, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CallSofiaOpenSignal signal(long id) {
        return new CallSofiaOpenSignal(id, "СОА26-КЦ01-" + id, 1007, "Улично платно", CallSofiaStatus.PROCESSING,
            "Дупка", NOW.minusSeconds(3600), Location.of(42.65, 23.38));
    }

    private static final class RecordingStore implements OpenSignalStore {
        Set<Long> known = Set.of();
        Map<Long, String> districtCodes;
        Instant seenAt;
        Instant agedBefore;
        boolean recorded;

        @Override
        public Set<Long> knownIds(Collection<Long> ids) {
            return known;
        }

        @Override
        public Departures recordReading(List<CallSofiaOpenSignal> onMap, Map<Long, String> districtCodes,
                                        Instant seenAt, Instant agedBefore) {
            this.recorded = true;
            this.districtCodes = districtCodes;
            this.seenAt = seenAt;
            this.agedBefore = agedBefore;
            return new Departures(0, 0);
        }
    }
}
