package org.example.nabat.media.application;

import org.example.nabat.media.application.port.in.LoadPhotoUseCase;
import org.example.nabat.media.application.port.in.StorePhotoUseCase;
import org.example.nabat.media.application.port.out.FileStoragePort;
import org.example.nabat.media.domain.UnsupportedFileTypeException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The translation between the media module's inbound ports and its storage port. Thin,
 * but the mapping is exactly where a field can be silently dropped — and the whole
 * module had no tests before this.
 */
@ExtendWith(MockitoExtension.class)
class PhotoStorageServiceTest {

    @Mock
    private FileStoragePort fileStoragePort;

    private PhotoStorageService service() {
        return new PhotoStorageService(fileStoragePort);
    }

    @Test
    void passesEveryUploadFieldThroughToStorage() {
        InputStream content = new ByteArrayInputStream(new byte[] {1, 2, 3});
        when(fileStoragePort.store(any())).thenReturn(
            new FileStoragePort.StoredFile("/api/v1/uploads/abc.jpg", "abc.jpg"));

        service().store(new StorePhotoUseCase.PhotoUpload("holiday.JPG", "image/jpeg", 3L, content));

        ArgumentCaptor<FileStoragePort.FileUpload> captor =
            ArgumentCaptor.forClass(FileStoragePort.FileUpload.class);
        org.mockito.Mockito.verify(fileStoragePort).store(captor.capture());
        FileStoragePort.FileUpload forwarded = captor.getValue();

        assertThat(forwarded.filename()).isEqualTo("holiday.JPG");
        assertThat(forwarded.contentType()).isEqualTo("image/jpeg");
        assertThat(forwarded.size()).isEqualTo(3L);
        assertThat(forwarded.content()).isSameAs(content);
    }

    @Test
    void returnsTheUrlStorageAssigned() {
        when(fileStoragePort.store(any())).thenReturn(
            new FileStoragePort.StoredFile("/api/v1/uploads/abc.jpg", "abc.jpg"));

        StorePhotoUseCase.StoredPhoto stored = service()
            .store(new StorePhotoUseCase.PhotoUpload("x.jpg", "image/jpeg", 1L, InputStream.nullInputStream()));

        assertThat(stored.url()).isEqualTo("/api/v1/uploads/abc.jpg");
    }

    @Test
    void letsARejectedFileTypeReachTheCaller() {
        when(fileStoragePort.store(any())).thenThrow(new UnsupportedFileTypeException("nope"));

        assertThatThrownBy(() -> service()
            .store(new StorePhotoUseCase.PhotoUpload("evil.jpg", "image/jpeg", 1L, InputStream.nullInputStream())))
            .isInstanceOf(UnsupportedFileTypeException.class);
    }

    @Test
    void mapsStoredContentOntoThePhotoContentShape() {
        InputStream content = new ByteArrayInputStream(new byte[] {9});
        when(fileStoragePort.load("abc.jpg"))
            .thenReturn(Optional.of(new FileStoragePort.StoredContent(content, "image/png", 42L)));

        Optional<LoadPhotoUseCase.PhotoContent> loaded = service().load("abc.jpg");

        assertThat(loaded).isPresent();
        assertThat(loaded.get().contentType()).isEqualTo("image/png");
        assertThat(loaded.get().size()).isEqualTo(42L);
        assertThat(loaded.get().content()).isSameAs(content);
    }

    @Test
    void reportsAMissingPhotoAsEmptyRatherThanThrowing() {
        when(fileStoragePort.load("gone.jpg")).thenReturn(Optional.empty());

        assertThat(service().load("gone.jpg")).isEmpty();
    }
}
