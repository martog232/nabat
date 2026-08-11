package org.example.nabat.media.application;

import org.example.nabat.media.application.port.out.FileStoragePort;
import org.example.nabat.media.application.port.out.PhotoReferencePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrphanedPhotoReclaimServiceTest {

    private static final Duration GRACE = Duration.ofHours(24);

    @Mock
    private FileStoragePort fileStorage;

    @Mock
    private PhotoReferencePort photoReferences;

    private OrphanedPhotoReclaimService service;

    @BeforeEach
    void setUp() {
        service = new OrphanedPhotoReclaimService(fileStorage, photoReferences, GRACE, 500);
    }

    @Test
    void deletesOnlyTheFilesNothingPointsAt() {
        when(fileStorage.listStoredBefore(any(Instant.class), anyInt()))
            .thenReturn(Set.of("orphan.jpg", "attached.jpg"));
        when(photoReferences.referencedAmong(any())).thenReturn(Set.of("attached.jpg"));
        when(fileStorage.delete("orphan.jpg")).thenReturn(true);

        assertEquals(1, service.reclaimOrphanedPhotos());

        verify(fileStorage).delete("orphan.jpg");
        verify(fileStorage, never()).delete("attached.jpg");
    }

    /**
     * The one that matters. The service decides what to delete from the absence of a
     * reference, so a lookup failure reported as "nothing is referenced" would delete every
     * candidate — the whole volume, the first time the database was unreachable.
     */
    @Test
    void deletesNothingWhenReferencesCannotBeDetermined() {
        when(fileStorage.listStoredBefore(any(Instant.class), anyInt()))
            .thenReturn(Set.of("a.jpg", "b.jpg"));
        when(photoReferences.referencedAmong(any()))
            .thenThrow(new IllegalStateException("database is down"));

        assertThrows(IllegalStateException.class, () -> service.reclaimOrphanedPhotos());

        verify(fileStorage, never()).delete(anyString());
    }

    @Test
    void doesNotConsultReferencesWhenThereAreNoCandidates() {
        when(fileStorage.listStoredBefore(any(Instant.class), anyInt())).thenReturn(Set.of());

        assertEquals(0, service.reclaimOrphanedPhotos());

        // No candidates means no question to ask; querying anyway would put a pointless
        // round trip on every sweep of an idle system.
        verifyNoInteractions(photoReferences);
        verify(fileStorage, never()).delete(anyString());
    }

    /** Files younger than the grace period are never even offered as candidates. */
    @Test
    void asksOnlyForFilesOlderThanTheGracePeriod() {
        when(fileStorage.listStoredBefore(any(Instant.class), anyInt())).thenReturn(Set.of());
        Instant before = Instant.now();

        service.reclaimOrphanedPhotos();

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(fileStorage).listStoredBefore(cutoff.capture(), anyInt());

        Instant expected = before.minus(GRACE);
        assertTrue(
            !cutoff.getValue().isBefore(expected.minusSeconds(5))
            && !cutoff.getValue().isAfter(expected.plusSeconds(5)),
            "cutoff should be roughly now minus the grace period, was " + cutoff.getValue()
        );
    }

    /**
     * A file that is gone by the time it is deleted — another replica swept it, or the
     * volume dropped it — is not counted and not an error.
     */
    @Test
    void doesNotCountFilesThatWereAlreadyGone() {
        when(fileStorage.listStoredBefore(any(Instant.class), anyInt())).thenReturn(Set.of("gone.jpg"));
        when(photoReferences.referencedAmong(any())).thenReturn(Set.of());
        when(fileStorage.delete("gone.jpg")).thenReturn(false);

        assertEquals(0, service.reclaimOrphanedPhotos());
    }

    @Test
    void respectsTheBatchSize() {
        var smallBatches = new OrphanedPhotoReclaimService(fileStorage, photoReferences, GRACE, 25);
        when(fileStorage.listStoredBefore(any(Instant.class), anyInt())).thenReturn(Set.of());

        smallBatches.reclaimOrphanedPhotos();

        verify(fileStorage).listStoredBefore(any(Instant.class), org.mockito.ArgumentMatchers.eq(25));
    }
}
