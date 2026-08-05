package org.example.nabat.media.application;

import org.example.nabat.media.application.port.in.LoadPhotoUseCase;
import org.example.nabat.media.application.port.in.StorePhotoUseCase;
import org.example.nabat.media.application.port.out.FileStoragePort;
import org.example.nabat.shared.UseCase;

import java.util.Optional;

/**
 * Translates between the media module's inbound ports and whatever storage backs them.
 *
 * <p>Thin today, deliberately: the traversal check and magic-byte sniffing live in the
 * storage adapter where they already worked, and no rule has yet been identified that
 * belongs at this level. Its value is the seam — the controller now names an inbound port
 * instead of an outbound one, so replacing the local filesystem with S3 or MinIO in
 * phase 4 touches the adapter and nothing that faces HTTP.
 */
@UseCase
public class PhotoStorageService implements StorePhotoUseCase, LoadPhotoUseCase {

    private final FileStoragePort fileStoragePort;

    public PhotoStorageService(FileStoragePort fileStoragePort) {
        this.fileStoragePort = fileStoragePort;
    }

    @Override
    public StoredPhoto store(PhotoUpload upload) {
        FileStoragePort.StoredFile stored = fileStoragePort.store(new FileStoragePort.FileUpload(
            upload.originalFilename(),
            upload.declaredContentType(),
            upload.sizeBytes(),
            upload.content()
        ));
        return new StoredPhoto(stored.url());
    }

    @Override
    public Optional<PhotoContent> load(String filename) {
        return fileStoragePort.load(filename)
            .map(stored -> new PhotoContent(stored.contentType(), stored.size(), stored.content()));
    }
}
