package org.example.nabat.media.adapter.in.scheduling;

import org.example.nabat.media.application.port.in.ReclaimOrphanedPhotosUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Clock that drives {@link ReclaimOrphanedPhotosUseCase}.
 *
 * <p>A driving adapter and nothing more: the schedule is infrastructure, the decision about
 * what may be deleted is not, so none of that logic lives here.
 *
 * <p>Off by default. Enabling a job whose whole purpose is deleting files should be a
 * decision someone makes per environment, and it is on in compose and the Helm chart where
 * an unbounded upload volume is a real cost.
 */
@Component
@ConditionalOnProperty(name = "nabat.storage.orphan-sweep.enabled", havingValue = "true")
public class OrphanedPhotoSweeper {

    private static final Logger log = LoggerFactory.getLogger(OrphanedPhotoSweeper.class);

    private final ReclaimOrphanedPhotosUseCase reclaimOrphanedPhotos;

    public OrphanedPhotoSweeper(ReclaimOrphanedPhotosUseCase reclaimOrphanedPhotos) {
        this.reclaimOrphanedPhotos = reclaimOrphanedPhotos;
    }

    /**
     * {@code fixedDelay}, not {@code fixedRate}: the gap is measured from the end of the
     * previous run, so a slow sweep cannot have a second one queued up behind it.
     */
    @Scheduled(
        fixedDelayString = "${nabat.storage.orphan-sweep.interval:PT1H}",
        initialDelayString = "${nabat.storage.orphan-sweep.initial-delay:PT5M}"
    )
    public void sweep() {
        try {
            reclaimOrphanedPhotos.reclaimOrphanedPhotos();
        } catch (RuntimeException e) {
            // Caught here rather than in the service, and only here: an escaping exception
            // from a @Scheduled method is logged by the framework but this keeps the
            // message specific. The service still refuses to delete anything when it
            // cannot establish what is referenced — that guarantee is upstream of this.
            log.warn("Orphaned-photo sweep failed; nothing was deleted. Retrying next run.", e);
        }
    }
}
