package org.example.nabat.media.adapter.out;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The listing and deletion half of the orphan sweep.
 *
 * <p>Separate from the storing/loading tests because these two methods are the ones that
 * can destroy data: everything the sweeper deletes, it deletes because this class said the
 * file was old enough to consider.
 */
class FileSystemStorageAdapterSweepTest {

    @TempDir
    Path uploadDir;

    private FileSystemStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        adapter = new FileSystemStorageAdapter(
            uploadDir.toString(), "/api/v1/uploads/", 10 * 1024 * 1024);
    }

    @Test
    void listsOnlyFilesOlderThanTheCutoff() throws IOException {
        Path old = writeFile("old.jpg", Instant.now().minus(Duration.ofHours(48)));
        writeFile("fresh.jpg", Instant.now());

        Set<String> found = adapter.listStoredBefore(Instant.now().minus(Duration.ofHours(24)), 500);

        // The fresh one is the whole point of the cutoff: it is very likely sitting in a
        // form the user has not submitted yet.
        assertThat(found).containsExactly(old.getFileName().toString());
    }

    /**
     * The directory is not guaranteed to hold only our files, and sweeping is destructive,
     * so anything without an extension this adapter assigns is left alone.
     */
    @Test
    void ignoresFilesItCouldNotHaveWritten() throws IOException {
        writeFile("notes.txt", Instant.now().minus(Duration.ofHours(48)));
        writeFile("no-extension", Instant.now().minus(Duration.ofHours(48)));
        Path ours = writeFile("ours.png", Instant.now().minus(Duration.ofHours(48)));

        Set<String> found = adapter.listStoredBefore(Instant.now(), 500);

        assertThat(found).containsExactly(ours.getFileName().toString());
    }

    @Test
    void honoursTheLimit() throws IOException {
        for (int i = 0; i < 5; i++) {
            writeFile("old-" + i + ".jpg", Instant.now().minus(Duration.ofHours(48)));
        }

        assertEquals(2, adapter.listStoredBefore(Instant.now(), 2).size());
    }

    @Test
    void deleteRemovesTheFileAndReportsIt() throws IOException {
        Path file = writeFile("doomed.jpg", Instant.now());

        assertTrue(adapter.delete("doomed.jpg"));
        assertFalse(Files.exists(file));
    }

    /** Losing a race against another replica is the expected outcome, not a failure. */
    @Test
    void deleteIsIdempotent() {
        assertFalse(adapter.delete("never-existed.jpg"));
    }

    /**
     * The sweeper passes names straight through from a directory listing, but the port is
     * public and a traversal attempt must not escape the upload directory — the failure
     * mode here is deleting an arbitrary file on the host.
     */
    @Test
    void deleteRefusesToEscapeTheUploadDirectory() throws IOException {
        Path outside = Files.createTempFile("outside", ".jpg");
        try {
            assertFalse(adapter.delete("../" + outside.getFileName()));
            assertTrue(Files.exists(outside), "a file outside the upload directory was deleted");
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    private Path writeFile(String name, Instant lastModified) throws IOException {
        Path file = uploadDir.resolve(name);
        Files.writeString(file, "content");
        Files.setLastModifiedTime(file, FileTime.from(lastModified));
        return file;
    }
}
