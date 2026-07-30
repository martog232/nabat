package org.example.nabat.media.application.port.in;

import java.io.InputStream;
import java.util.Optional;

/** Reads a stored photo back out. */
public interface LoadPhotoUseCase {

    /** @return empty when no such photo exists, or the name is not one we would have issued */
    Optional<PhotoContent> load(String filename);

    /**
     * @param contentType the type determined by magic-byte sniffing when the file was
     *                    written, never re-derived from the filename
     */
    record PhotoContent(String contentType, long size, InputStream content) {}
}
