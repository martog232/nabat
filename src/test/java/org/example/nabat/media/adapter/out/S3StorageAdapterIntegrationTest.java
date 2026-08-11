package org.example.nabat.media.adapter.out;

import org.example.nabat.media.application.port.out.FileStoragePort;
import org.example.nabat.media.domain.UnsupportedFileTypeException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MinIOContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the S3 adapter against a real MinIO.
 *
 * <p>Against a mocked {@code S3Client} this would test the mock. The things that actually
 * go wrong here are properties of the protocol and the server: whether path-style
 * addressing is on (without it the SDK sends {@code bucket.localhost} and nothing
 * resolves), whether the recorded content type survives a round trip, whether a missing key
 * raises {@code NoSuchKeyException} rather than a generic error.
 *
 * <p>The adapter is expected to behave the same as {@link FileSystemStorageAdapter} —
 * generated names, magic-byte typing, idempotent delete, an age-filtered listing — because
 * everything above the port is written against one contract and cannot tell which storage
 * it has.
 */
@Testcontainers(disabledWithoutDocker = true)
class S3StorageAdapterIntegrationTest {

    private static final String BUCKET = "nabat-test-uploads";

    // A 1x1 GIF: small, and a real magic-byte match rather than a string pretending to be
    // an image. The adapter sniffs content, so arbitrary bytes are rejected by design.
    private static final byte[] GIF = {
        'G', 'I', 'F', '8', '9', 'a', 1, 0, 1, 0, (byte) 0x80, 0
    };

    @Container
    static final MinIOContainer MINIO = new MinIOContainer("minio/minio:RELEASE.2024-08-29T01-40-52Z");

    private static S3Client s3;

    private FileStoragePort storage;

    @BeforeAll
    static void createBucket() {
        s3 = S3Client.builder()
                .endpointOverride(URI.create(MINIO.getS3URL()))
                .region(Region.US_EAST_1)
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(MINIO.getUserName(), MINIO.getPassword())))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .httpClient(UrlConnectionHttpClient.create())
                .build();

        s3.createBucket(CreateBucketRequest.builder().bucket(BUCKET).build());
    }

    @BeforeEach
    void setUp() {
        emptyBucket();
        storage = new S3StorageAdapter(s3, BUCKET, "/api/v1/uploads/", 10 * 1024 * 1024);
    }

    @Test
    void storesAnImageAndReadsItBack() throws IOException {
        FileStoragePort.StoredFile stored = storage.store(upload("photo.gif", GIF));

        assertTrue(stored.url().startsWith("/api/v1/uploads/"));
        assertTrue(stored.filename().endsWith(".gif"), "extension is derived from content: " + stored.filename());
        // The client's filename is never reused as the storage name.
        assertFalse(stored.filename().contains("photo"));

        FileStoragePort.StoredContent loaded = storage.load(stored.filename()).orElseThrow();
        assertEquals("image/gif", loaded.contentType());
        assertEquals(GIF.length, loaded.size());
        try (InputStream content = loaded.content()) {
            assertArrayEquals(GIF, content.readAllBytes());
        }
    }

    @Test
    void rejectsContentThatIsNotAPermittedImage() {
        byte[] notAnImage = "<?php echo 'hi'; ?>".getBytes(StandardCharsets.UTF_8);

        // Named .jpg and declared as an image: both are the client's word, and neither is
        // trusted. Only the magic bytes decide.
        assertThrows(
            UnsupportedFileTypeException.class,
            () -> storage.store(upload("innocent.jpg", notAnImage))
        );
    }

    @Test
    void rejectsAnUploadLargerThanTheLimit() {
        var small = new S3StorageAdapter(s3, BUCKET, "/api/v1/uploads/", 8);

        assertThrows(
            IllegalArgumentException.class,
            () -> small.store(upload("big.gif", GIF))
        );
    }

    /**
     * The declared size is the client's claim. A body larger than it must still be refused,
     * or a lying Content-Length puts an arbitrarily large object in the bucket.
     */
    @Test
    void rejectsABodyLargerThanItsDeclaredSize() {
        var small = new S3StorageAdapter(s3, BUCKET, "/api/v1/uploads/", 8);
        var understated = new FileStoragePort.FileUpload(
            "big.gif", "image/gif", 4, new ByteArrayInputStream(GIF));

        assertThrows(IllegalArgumentException.class, () -> small.store(understated));
    }

    @Test
    void returnsEmptyForAKeyThatIsNotThere() {
        assertTrue(storage.load("00000000-0000-0000-0000-000000000000.gif").isEmpty());
    }

    @Test
    void returnsEmptyForAnExtensionThisApplicationNeverAssigns() {
        assertTrue(storage.load("something.txt").isEmpty());
    }

    @Test
    void deleteRemovesTheObjectAndIsIdempotent() {
        FileStoragePort.StoredFile stored = storage.store(upload("photo.gif", GIF));

        assertTrue(storage.delete(stored.filename()));
        assertTrue(storage.load(stored.filename()).isEmpty());
        // Second call: S3 reports success for a key that was never there, so this would
        // return true without the existence check — and the sweeper would over-report.
        assertFalse(storage.delete(stored.filename()));
    }

    @Test
    void listsOnlyObjectsOlderThanTheCutoff() {
        FileStoragePort.StoredFile fresh = storage.store(upload("fresh.gif", GIF));

        // Everything just written is newer than a cutoff in the past, so nothing is a
        // candidate — the guard that stops a sweep eating uploads still being attached.
        assertThat(storage.listStoredBefore(Instant.now().minus(1, ChronoUnit.HOURS), 500))
            .isEmpty();

        assertThat(storage.listStoredBefore(Instant.now().plus(1, ChronoUnit.MINUTES), 500))
            .containsExactly(fresh.filename());
    }

    @Test
    void listingIgnoresObjectsThisApplicationCouldNotHaveWritten() {
        s3.putObject(
            PutObjectRequest.builder().bucket(BUCKET).key("stray.txt").build(),
            RequestBody.fromString("not ours"));
        FileStoragePort.StoredFile ours = storage.store(upload("photo.gif", GIF));

        Set<String> listed = storage.listStoredBefore(Instant.now().plus(1, ChronoUnit.MINUTES), 500);

        assertThat(listed).containsExactly(ours.filename());
    }

    @Test
    void listingHonoursTheLimit() {
        storage.store(upload("a.gif", GIF));
        storage.store(upload("b.gif", GIF));
        storage.store(upload("c.gif", GIF));

        assertEquals(2, storage.listStoredBefore(Instant.now().plus(1, ChronoUnit.MINUTES), 2).size());
    }

    private FileStoragePort.FileUpload upload(String filename, byte[] content) {
        return new FileStoragePort.FileUpload(
            filename, "image/gif", content.length, new ByteArrayInputStream(content));
    }

    private void emptyBucket() {
        s3.listObjectsV2Paginator(ListObjectsV2Request.builder().bucket(BUCKET).build())
            .contents()
            .forEach(object -> s3.deleteObject(b -> b.bucket(BUCKET).key(object.key())));
    }
}
