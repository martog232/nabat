package org.example.nabat.callsofia.application;

import org.example.nabat.callsofia.application.port.in.SyncOpenCallSofiaSignalsUseCase;
import org.example.nabat.callsofia.application.port.out.CallSofiaMap;
import org.example.nabat.callsofia.application.port.out.DistrictLocatorPort;
import org.example.nabat.callsofia.application.port.out.OpenSignalStore;
import org.example.nabat.callsofia.application.port.out.OpenSignalStore.Departures;
import org.example.nabat.callsofia.domain.CallSofiaOpenSignal;
import org.example.nabat.shared.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One reading of Call Sofia's map.
 *
 * <p>Not {@code @Transactional}: the first step is an HTTP request to someone else's
 * server, and a transaction around it would hold a pooled connection for as long as that
 * server takes. The write at the end is one transaction of its own, in the store.
 */
@UseCase
public class OpenCallSofiaSignalsSyncService implements SyncOpenCallSofiaSignalsUseCase {

    private static final Logger log = LoggerFactory.getLogger(OpenCallSofiaSignalsSyncService.class);

    /** How far back the map shows reports. Its own filter, „Последните 30 дни“. */
    static final Duration MAP_WINDOW = Duration.ofDays(30);

    /**
     * Slack on the 30 days, in the direction that errs towards "aged out". Call Sofia's
     * clock and ours differ a little, and a report filed exactly 30 days ago can be gone
     * from the map a few minutes before our arithmetic says it should be. Calling that a
     * status change would credit the municipality with an answer it did not give.
     */
    static final Duration CLOCK_SLACK = Duration.ofMinutes(15);

    private final CallSofiaMap map;
    private final OpenSignalStore store;
    private final DistrictLocatorPort districts;
    private final Clock clock;

    /** Marked, because a second constructor exists for tests to pass a fixed clock. */
    @Autowired
    public OpenCallSofiaSignalsSyncService(CallSofiaMap map, OpenSignalStore store, DistrictLocatorPort districts) {
        this(map, store, districts, Clock.systemUTC());
    }

    OpenCallSofiaSignalsSyncService(CallSofiaMap map, OpenSignalStore store, DistrictLocatorPort districts, Clock clock) {
        this.map = map;
        this.store = store;
        this.districts = districts;
        this.clock = clock;
    }

    @Override
    public SyncSummary sync() {
        List<CallSofiaOpenSignal> onMap = map.openSignals();

        // An empty map is not a city with nothing in processing — the site always has
        // thousands. It is a reading that went wrong in a way the client did not detect,
        // and recording it would mark every report as gone at once.
        if (onMap.isEmpty()) {
            throw new IllegalStateException("Call Sofia's map came back empty; not recording it");
        }

        // Microseconds, because that is what TIMESTAMPTZ keeps: the store tells this
        // reading's rows from older ones by comparing seen-at for equality.
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);

        Set<Long> known = store.knownIds(onMap.stream().map(CallSofiaOpenSignal::id).toList());
        Map<Long, String> districtCodes = new HashMap<>();
        int newlySeen = 0;
        for (CallSofiaOpenSignal signal : onMap) {
            if (known.contains(signal.id())) {
                continue;
            }
            newlySeen++;
            if (signal.location() != null) {
                districts.districtCodeAt(signal.location())
                    .ifPresent(code -> districtCodes.put(signal.id(), code));
            }
        }

        Departures departures = store.recordReading(onMap, districtCodes, now, now.minus(MAP_WINDOW).plus(CLOCK_SLACK));

        SyncSummary summary = new SyncSummary(onMap.size(), newlySeen, departures.agedOut(), departures.noLongerProcessing());
        log.info("Call Sofia map read: {}", summary);
        return summary;
    }
}
