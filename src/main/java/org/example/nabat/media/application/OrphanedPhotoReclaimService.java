package org.example.nabat.media.application;

import org.example.nabat.media.application.port.in.ReclaimOrphanedPhotosUseCase;
import org.example.nabat.media.application.port.out.FileStoragePort;
import org.example.nabat.media.application.port.out.PhotoReferencePort;
import org.example.nabat.shared.UseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Reclaims uploaded photos that no alert refers to.
 *
 * <h2>The dangerous shape of this job</h2>
 * It decides what to delete from the <em>absence</em> of a reference, so every way of
 * wrongly concluding "nothing points at this" destroys a user's data. Two guards follow
 * from that, and both matter more than the feature itself:
 *
 * <ul>
 *   <li><strong>An age cutoff.</strong> A photo uploaded seconds ago is almost certainly
 *       sitting in a form that has not been submitted. Only files older than
 *       {@code nabat.storage.orphan-grace} are considered, and the default is generous
 *       precisely because being late costs disk while being early costs a photo.</li>
 *   <li><strong>Failure means stop, never "delete".</strong> If the reference lookup
 *       throws, this method propagates rather than treating the empty result as "none are
 *       referenced" — that reading would delete every photo on the volume the first time
 *       the database was unreachable. Nothing is deleted before the answer is in hand.</li>
 * </ul>
 *
 * <h2>Concurrency</h2>
 * Two replicas may sweep at once. That is safe rather than coordinated: deletion is
 * idempotent, so the loser of the race simply deletes nothing. With local-disk storage
 * each replica only ever sees its own files anyway.
 */
@UseCase
public class OrphanedPhotoReclaimService implements ReclaimOrphanedPhotosUseCase {

    private static final Logger log = LoggerFactory.getLogger(OrphanedPhotoReclaimService.class);

    private final FileStoragePort fileStorage;
    private final PhotoReferencePort photoReferences;
    private final Duration grace;
    private final int batchSize;

    public OrphanedPhotoReclaimService(
            FileStoragePort fileStorage,
            PhotoReferencePort photoReferences,
            @Value("${nabat.storage.orphan-grace:PT24H}") Duration grace,
            @Value("${nabat.storage.orphan-batch-size:500}") int batchSize
    ) {
        this.fileStorage = fileStorage;
        this.photoReferences = photoReferences;
        this.grace = grace;
        this.batchSize = batchSize;
    }

    @Override
    public int reclaimOrphanedPhotos() {
        Instant cutoff = Instant.now().minus(grace);
        Set<String> candidates = fileStorage.listStoredBefore(cutoff, batchSize);

        if (candidates.isEmpty()) {
            return 0;
        }

        // Deliberately outside any try/catch. A failure here has to abort the sweep: the
        // decision below is "delete everything not in this set", so an exception swallowed
        // into an empty set would delete every candidate.
        Set<String> referenced = photoReferences.referencedAmong(candidates);

        int deleted = 0;
        for (String candidate : candidates) {
            if (!referenced.contains(candidate) && fileStorage.delete(candidate)) {
                deleted++;
            }
        }

        if (deleted > 0) {
            log.info("Reclaimed {} orphaned photo(s) older than {} ({} candidates, {} still referenced)",
                    deleted, grace, candidates.size(), referenced.size());
        }
        return deleted;
    }
}
