package org.example.nabat.media.application.port.in;

/**
 * Deletes stored photos that nothing points at.
 *
 * <p>Uploading is two steps — {@code POST /api/v1/uploads} then {@code POST
 * /api/v1/alerts} carrying the returned URL — so a photo whose alert is never submitted
 * has nothing referencing it and nothing to remove it. That is by design in the upload
 * flow (it lets the client retry the alert without re-uploading), but it leaves the
 * volume growing with files no request will ever ask for.
 */
public interface ReclaimOrphanedPhotosUseCase {

    /**
     * Runs one pass.
     *
     * @return how many files were deleted
     * @throws RuntimeException if references could not be determined; nothing is deleted
     *                          in that case
     */
    int reclaimOrphanedPhotos();
}
