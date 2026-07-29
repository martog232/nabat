package org.example.nabat.adapter.out.storage;

import org.example.nabat.application.port.out.FileStoragePort;
import org.example.nabat.domain.exception.UnsupportedFileTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.UUID;

/**
 * Stores alert photos on the local filesystem.
 *
 * <p><strong>Multi-instance caveat:</strong> local disk means a photo written by one
 * replica is not readable by another unless the volume is {@code ReadWriteMany}. With
 * {@code nabatApp.replicas: 2} in the Helm values, roughly half of photo reads will
 * miss. An S3/MinIO adapter is the real fix; this is called out in AGENTS.md as a
 * known gap.
 */
@Component
public class FileSystemStorageAdapter implements FileStoragePort {

    private static final Logger log = LoggerFactory.getLogger(FileSystemStorageAdapter.class);

    private final Path uploadDir;
    private final String servePath;
    private final long maxBytes;

    public FileSystemStorageAdapter(
            @Value("${nabat.storage.upload-dir}") String uploadDir,
            @Value("${nabat.storage.serve-path}") String servePath,
            @Value("${nabat.storage.max-file-size-bytes:10485760}") long maxBytes
    ) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.servePath = servePath;
        this.maxBytes = maxBytes;
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new IllegalStateException("Could not create upload directory: " + this.uploadDir, e);
        }
        log.info("File storage initialized: dir={}, servePath={}, maxBytes={}",
                this.uploadDir, this.servePath, this.maxBytes);
    }

    @Override
    public StoredFile store(FileUpload upload) {
        if (upload.size() <= 0) {
            throw new IllegalArgumentException("Cannot store an empty file");
        }
        if (upload.size() > maxBytes) {
            throw new IllegalArgumentException(
                    "File exceeds the maximum size of " + maxBytes + " bytes");
        }

        // Buffered so the sniffed header can be pushed back before copying.
        try (BufferedInputStream content = new BufferedInputStream(upload.content())) {
            ImageContentType type = detectType(content);

            // Name and extension are generated, never taken from the client. The old code
            // used the client's extension verbatim, so the served Content-Type was
            // attacker-chosen.
            String filename = UUID.randomUUID() + type.extension();
            Path targetPath = resolveWithin(filename);

            long written = Files.copy(content, targetPath);
            if (written > maxBytes) {
                // Belt and braces: the declared size could have lied.
                Files.deleteIfExists(targetPath);
                throw new IllegalArgumentException(
                        "File exceeds the maximum size of " + maxBytes + " bytes");
            }

            log.info("Stored upload as {} ({}, {} bytes)", filename, type.mimeType(), written);
            return new StoredFile(servePath + filename, filename);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to store the uploaded file", e);
        }
    }

    @Override
    public Optional<StoredContent> load(String filename) {
        // The type comes from the extension *this class* assigned at write time, so it
        // is trusted; an unrecognised extension means the file was not written by us.
        Optional<ImageContentType> type = ImageContentType.fromExtension(filename);
        if (type.isEmpty()) {
            return Optional.empty();
        }

        try {
            Path filePath = resolveWithin(filename);
            if (!Files.isRegularFile(filePath)) {
                return Optional.empty();
            }
            return Optional.of(new StoredContent(
                    Files.newInputStream(filePath),
                    type.get().mimeType(),
                    Files.size(filePath)
            ));
        } catch (IOException | IllegalArgumentException e) {
            log.debug("Could not load upload {}: {}", filename, e.getMessage());
            return Optional.empty();
        }
    }

    private ImageContentType detectType(BufferedInputStream content) throws IOException {
        content.mark(ImageContentType.SNIFF_LENGTH);
        byte[] header = content.readNBytes(ImageContentType.SNIFF_LENGTH);
        content.reset();

        return ImageContentType.detect(header)
                .orElseThrow(() -> new UnsupportedFileTypeException(
                        "Only " + String.join(", ", ImageContentType.permittedMimeTypes())
                                + " images are accepted"));
    }

    /** Resolves a name inside the upload directory, refusing anything that escapes it. */
    private Path resolveWithin(String filename) {
        Path resolved = uploadDir.resolve(filename).normalize();
        if (!resolved.startsWith(uploadDir)) {
            throw new IllegalArgumentException("Path traversal detected in filename: " + filename);
        }
        return resolved;
    }
}
