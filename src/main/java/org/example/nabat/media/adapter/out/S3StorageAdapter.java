package org.example.nabat.media.adapter.out;

import org.example.nabat.media.application.port.out.FileStoragePort;
import org.example.nabat.media.domain.UnsupportedFileTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Stores alert photos in an S3-compatible bucket (MinIO locally, S3 in a cloud).
 *
 * <p>The reason this exists is {@code replicas: 2}. {@link FileSystemStorageAdapter} writes
 * to local disk, so a photo written by one pod is simply absent from the other — roughly
 * half of reads 404 for no reason the user can understand, and a pod restart takes its
 * uploads with it. Object storage removes the problem rather than papering over it with a
 * ReadWriteMany volume.
 *
 * <p>Which adapter is active is decided by {@code nabat.storage.type}; both implement the
 * same port and nothing above them changes.
 *
 * <h2>Why the whole upload is buffered</h2>
 * A single {@code PutObject} needs the content length up front, and the type check needs to
 * read the first bytes before deciding. Rather than read the stream twice or start a
 * multipart upload for a photo, the body is read into memory with a hard ceiling of
 * {@code maxBytes} — the same limit the multipart layer already enforces. The trade is
 * explicit: at 10 MB per upload this is bounded and small, and it would need revisiting
 * only if the limit grew or uploads became video.
 *
 * <h2>What this does not change</h2>
 * Photos are still served <em>through</em> the application at {@code /api/v1/uploads/...},
 * behind authentication. Handing clients presigned URLs would take the bytes off the
 * application entirely and make CDN caching possible, but it changes the API contract and
 * belongs with the media-service split (phase 4) rather than with swapping the storage.
 */
public class S3StorageAdapter implements FileStoragePort {

    private static final Logger log = LoggerFactory.getLogger(S3StorageAdapter.class);

    private final S3Client s3;
    private final String bucket;
    private final String servePath;
    private final long maxBytes;

    public S3StorageAdapter(
            S3Client s3,
            @Value("${nabat.storage.s3.bucket}") String bucket,
            @Value("${nabat.storage.serve-path}") String servePath,
            @Value("${nabat.storage.max-file-size-bytes:10485760}") long maxBytes
    ) {
        this.s3 = s3;
        this.bucket = bucket;
        this.servePath = servePath;
        this.maxBytes = maxBytes;
        log.info("S3 photo storage initialized: bucket={}, servePath={}, maxBytes={}",
                bucket, servePath, maxBytes);
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

        byte[] content = readWithinLimit(upload.content());
        ImageContentType type = ImageContentType.detect(content)
                .orElseThrow(() -> new UnsupportedFileTypeException(
                        "Only " + String.join(", ", ImageContentType.permittedMimeTypes())
                                + " images are accepted"));

        // Name and extension are generated, never taken from the client — the same rule as
        // the filesystem adapter, for the same reason: a client-supplied extension decides
        // the Content-Type this file is later served with.
        String filename = UUID.randomUUID() + type.extension();

        s3.putObject(
                PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(filename)
                        // Recorded so `load` does not have to re-sniff, and so anything
                        // reading the bucket directly sees the right type.
                        .contentType(type.mimeType())
                        .contentLength((long) content.length)
                        .build(),
                RequestBody.fromBytes(content)
        );

        log.info("Stored upload as {} ({}, {} bytes)", filename, type.mimeType(), content.length);
        return new StoredFile(servePath + filename, filename);
    }

    @Override
    public Optional<StoredContent> load(String filename) {
        // Same guard as the filesystem adapter: an extension this application does not
        // assign means the object was not written by us.
        if (ImageContentType.fromExtension(filename).isEmpty()) {
            return Optional.empty();
        }

        try {
            ResponseInputStream<GetObjectResponse> object = s3.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(filename).build());
            GetObjectResponse response = object.response();

            return Optional.of(new StoredContent(
                    object,
                    response.contentType(),
                    response.contentLength()
            ));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        } catch (S3Exception e) {
            log.warn("Could not load upload {}: {}", filename, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public Set<String> listStoredBefore(Instant cutoff, int limit) {
        Set<String> names = new LinkedHashSet<>();

        // Paginated: a bucket holds far more than one response can carry, and asking for
        // everything at once is how a sweep turns into an outage. The iterator stops as
        // soon as the limit is met.
        var pages = s3.listObjectsV2Paginator(
                ListObjectsV2Request.builder().bucket(bucket).build());

        for (S3Object object : pages.contents()) {
            if (names.size() >= limit) {
                break;
            }
            if (object.lastModified().isBefore(cutoff)
                    && ImageContentType.fromExtension(object.key()).isPresent()) {
                names.add(object.key());
            }
        }
        return names;
    }

    @Override
    public boolean delete(String filename) {
        // S3 deletes are idempotent and report success for a key that was never there, so
        // "did this call remove something" needs the head first. The sweeper only uses the
        // answer for its count, and a lost race here is expected rather than exceptional.
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(bucket).key(filename).build());
        } catch (NoSuchKeyException e) {
            return false;
        }

        try {
            s3.deleteObject(builder -> builder.bucket(bucket).key(filename));
            return true;
        } catch (S3Exception e) {
            log.debug("Could not delete upload {}: {}", filename, e.getMessage());
            return false;
        }
    }

    /**
     * Reads at most {@code maxBytes}, failing if there is more.
     *
     * <p>Reads one byte beyond the limit deliberately: a stream that stops exactly at the
     * limit is fine, and one that does not is over it. Trusting the declared size instead
     * would let a lying {@code Content-Length} put an arbitrarily large object in the
     * bucket.
     */
    private byte[] readWithinLimit(InputStream content) {
        try (InputStream stream = content) {
            byte[] bytes = stream.readNBytes((int) Math.min(maxBytes + 1, Integer.MAX_VALUE));
            if (bytes.length > maxBytes) {
                throw new IllegalArgumentException(
                        "File exceeds the maximum size of " + maxBytes + " bytes");
            }
            if (bytes.length == 0) {
                throw new IllegalArgumentException("Cannot store an empty file");
            }
            return bytes;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read the uploaded file", e);
        }
    }
}
