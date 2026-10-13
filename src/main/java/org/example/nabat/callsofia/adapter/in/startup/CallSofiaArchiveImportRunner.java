package org.example.nabat.callsofia.adapter.in.startup;

import org.example.nabat.callsofia.application.port.in.ImportCallSofiaArchiveUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Runs {@link ImportCallSofiaArchiveUseCase} once, after startup, when asked to.
 *
 * <p>The archive stopped being updated in August 2026, so this is a job run once per
 * database rather than a schedule. Off by default: it reads a few hundred megabytes from
 * someone else's server, and that should be a decision, not a side effect of starting the
 * application. Set {@code NABAT_CALLSOFIA_IMPORT_ENABLED=true} for one start and back again.
 * Starting it twice is harmless — the import is idempotent — only slow.
 *
 * <p>{@code @Async}, because {@code ApplicationReadyEvent} listeners run before readiness is
 * reported, and the import takes minutes. Run on the shared task executor, where it holds
 * one thread for that long.
 */
@Component
@ConditionalOnProperty(name = "nabat.callsofia.import.enabled", havingValue = "true")
public class CallSofiaArchiveImportRunner {

    private static final Logger log = LoggerFactory.getLogger(CallSofiaArchiveImportRunner.class);

    private final ImportCallSofiaArchiveUseCase importArchive;

    public CallSofiaArchiveImportRunner(ImportCallSofiaArchiveUseCase importArchive) {
        this.importArchive = importArchive;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void importOnStartup() {
        log.info("Importing the Call Sofia archive");
        try {
            importArchive.importArchive();
        } catch (RuntimeException e) {
            // Whatever was committed stays; running the import again resumes over it.
            log.error("Call Sofia archive import failed; batches already written are kept", e);
        }
    }
}
