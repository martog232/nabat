package org.example.nabat.callsofia.adapter.in.scheduling;

import org.example.nabat.callsofia.application.port.in.SyncOpenCallSofiaSignalsUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Clock that drives {@link SyncOpenCallSofiaSignalsUseCase}.
 *
 * <p>Off by default, and hourly when on. Every reading is two requests to a municipal
 * server that never agreed to serve this application, so the interval is set by courtesy,
 * not by freshness: a report waits days for an answer, and an hour's delay in noticing
 * changes nothing anyone reads from this. One replica only — two would each read the map.
 */
@Component
@ConditionalOnProperty(name = "nabat.callsofia.live.enabled", havingValue = "true")
public class CallSofiaMapPoller {

    private static final Logger log = LoggerFactory.getLogger(CallSofiaMapPoller.class);

    private final SyncOpenCallSofiaSignalsUseCase sync;

    public CallSofiaMapPoller(SyncOpenCallSofiaSignalsUseCase sync) {
        this.sync = sync;
    }

    /** {@code fixedDelay}: a slow reading cannot have the next one queued behind it. */
    @Scheduled(
        fixedDelayString = "${nabat.callsofia.live.interval:PT1H}",
        initialDelayString = "${nabat.callsofia.live.initial-delay:PT2M}"
    )
    public void poll() {
        try {
            sync.sync();
        } catch (RuntimeException e) {
            // Nothing was recorded: the write is one transaction after a complete reading.
            // Retrying sooner would only ask a server that is failing, or has changed, again.
            log.warn("Reading Call Sofia's map failed; nothing recorded. Retrying next run.", e);
        }
    }
}
