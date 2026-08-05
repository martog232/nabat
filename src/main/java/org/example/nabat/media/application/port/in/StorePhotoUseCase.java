package org.example.nabat.media.application.port.in;

import java.io.InputStream;

/**
 * Stores an uploaded photo and returns the URL an alert can reference.
 *
 * <p>Exists because {@code UploadController} used to be wired straight to
 * {@code FileStoragePort}: the only feature in the codebase with no application layer at
 * all, so a controller depended on an outbound port. Validation and content-type
 * sniffing already lived behind the storage adapter, which made the shortcut look
 * harmless — but it left nowhere to put a rule that is neither HTTP nor filesystem, and
 * it is the seam that has to exist before media becomes its own service.
 */
public interface StorePhotoUseCase {

    StoredPhoto store(PhotoUpload upload);

    /**
     * @param declaredContentType what the client claimed. Advisory only — the storage
     *                            adapter sniffs magic bytes and records what it finds.
     * @param content             streamed, never buffered whole; the caller owns closing it.
     */
    record PhotoUpload(
        String originalFilename,
        String declaredContentType,
        long sizeBytes,
        InputStream content
    ) {}

    /** @param url path clients reference, e.g. {@code /api/v1/uploads/<uuid>.jpg} */
    record StoredPhoto(String url) {}
}
