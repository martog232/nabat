package org.example.nabat.media.application.port.out;

import java.io.InputStream;
import java.util.Optional;

/**
 * Storage for user-uploaded alert photos.
 *
 * <p>Deliberately takes bytes and a declared content type rather than a
 * {@code MultipartFile}: that pulled {@code org.springframework.web.multipart} into
 * the application layer's port definition, and made the port unusable by anything
 * other than a servlet upload.
 *
 * <p>The port also exposes {@link #load} so callers do not have to touch the
 * filesystem themselves. {@code UploadController} previously read
 * {@code nabat.storage.upload-dir} and did its own {@code Files} calls, duplicating
 * path resolution and traversal checks that belong behind this interface.
 */
public interface FileStoragePort {

    /**
     * Stores an image and returns the relative URL that serves it.
     *
     * @throws org.example.nabat.media.domain.UnsupportedFileTypeException if the
     *         content is not one of the permitted image formats
     */
    StoredFile store(FileUpload upload);

    /** Opens a stored file, or empty if there is no such file. */
    Optional<StoredContent> load(String filename);

    /**
     * @param filename    the name the client supplied, used only for its extension
     * @param contentType the declared MIME type — never trusted on its own
     * @param size        length in bytes
     */
    record FileUpload(String filename, String contentType, long size, InputStream content) {
    }

    /**
     * @param url      relative URL for retrieval, e.g. {@code /api/v1/uploads/abc.jpg}
     * @param filename the generated storage name
     */
    record StoredFile(String url, String filename) {
    }

    /**
     * @param contentType the type determined when the file was stored, not re-sniffed
     *                    at read time
     */
    record StoredContent(InputStream content, String contentType, long size) {
    }
}
