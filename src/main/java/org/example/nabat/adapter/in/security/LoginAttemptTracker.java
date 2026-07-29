package org.example.nabat.adapter.in.security;

import org.example.nabat.application.port.out.LoginAttemptPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Counts recent failed logins per email and per client IP and logs when either
 * crosses an alerting threshold.
 *
 * <p>Observability only — it never blocks a request. Throttling lives at the Kong
 * gateway.
 *
 * <h2>Bounded by construction</h2>
 * The previous implementation kept two unbounded {@link java.util.concurrent.ConcurrentHashMap}s
 * keyed by email and IP, and only ever removed <em>entries within</em> each list —
 * never the keys themselves. Anyone could grow the maps without limit simply by
 * failing logins against distinct addresses, and every failed attempt walked the
 * whole of both maps to prune old records, so the cost of an attack grew with the
 * size of the attack.
 *
 * <p>Both maps are now fixed-capacity LRU caches: memory is capped regardless of
 * input, and pruning touches only the key being recorded. Evicting a cold key is
 * harmless here — losing the count for an address that has not failed recently
 * costs nothing, since the point is to spot bursts.
 */
@Component
public class LoginAttemptTracker implements LoginAttemptPort {

    private static final Logger logger = LoggerFactory.getLogger(LoginAttemptTracker.class);

    private static final Duration WINDOW = Duration.ofMinutes(60);

    /**
     * Alert once this many failures have accumulated in the window. The old check was
     * {@code count > ALERT_THRESHOLD}, which fired on the 11th attempt while the
     * comment beside it promised the 10th.
     */
    private static final int ALERT_THRESHOLD = 10;

    /** Distinct identities tracked per dimension before the coldest is dropped. */
    private static final int MAX_TRACKED_KEYS = 10_000;

    private final Map<String, Window> failuresByEmail = boundedLruMap();
    private final Map<String, Window> failuresByIp = boundedLruMap();

    @Override
    public void recordFailure(String email, String clientIp) {
        int emailFailures = record(failuresByEmail, email);
        if (emailFailures >= ALERT_THRESHOLD) {
            logger.warn("Possible brute force: {} failed login attempts for one email in the last {} minutes",
                emailFailures, WINDOW.toMinutes());
        }

        int ipFailures = record(failuresByIp, clientIp);
        if (ipFailures >= ALERT_THRESHOLD) {
            logger.warn("Possible brute force: {} failed login attempts from IP {} in the last {} minutes",
                ipFailures, clientIp, WINDOW.toMinutes());
        }
    }

    @Override
    public void recordSuccess(String email, String clientIp) {
        // Only the email history is cleared. A shared IP (office NAT, mobile carrier)
        // may be the source of a genuine attack alongside legitimate logins, so one
        // success there must not reset the count.
        synchronized (failuresByEmail) {
            failuresByEmail.remove(email);
        }
    }

    /** Failures recorded for this email inside the current window. */
    public int getFailedAttemptCountForEmail(String email) {
        return count(failuresByEmail, email);
    }

    /** Failures recorded for this IP inside the current window. */
    public int getFailedAttemptCountForIp(String clientIp) {
        return count(failuresByIp, clientIp);
    }

    private int record(Map<String, Window> store, String key) {
        if (key == null || key.isBlank()) {
            return 0;
        }
        Instant now = Instant.now();
        synchronized (store) {
            Window window = store.computeIfAbsent(key, k -> new Window());
            return window.add(now, WINDOW);
        }
    }

    private int count(Map<String, Window> store, String key) {
        if (key == null) {
            return 0;
        }
        synchronized (store) {
            Window window = store.get(key);
            return window == null ? 0 : window.countSince(Instant.now().minus(WINDOW));
        }
    }

    private static Map<String, Window> boundedLruMap() {
        return Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Window> eldest) {
                return size() > MAX_TRACKED_KEYS;
            }
        });
    }

    /**
     * A fixed-size ring of recent failure timestamps.
     *
     * <p>Capped at {@link #ALERT_THRESHOLD} entries because nothing above the
     * threshold changes the decision — there is no reason to retain a million
     * timestamps for one key to conclude "more than ten".
     */
    private static final class Window {
        private final long[] timestamps = new long[ALERT_THRESHOLD];
        private int size;
        private int next;

        /** @return the number of failures inside {@code retention}, after adding this one */
        int add(Instant at, Duration retention) {
            timestamps[next] = at.toEpochMilli();
            next = (next + 1) % timestamps.length;
            if (size < timestamps.length) {
                size++;
            }
            return countSince(at.minus(retention));
        }

        int countSince(Instant cutoff) {
            long cutoffMillis = cutoff.toEpochMilli();
            int count = 0;
            for (int i = 0; i < size; i++) {
                if (timestamps[i] >= cutoffMillis) {
                    count++;
                }
            }
            return count;
        }
    }
}
