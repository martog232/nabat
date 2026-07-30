package org.example.nabat.media.adapter.out;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Magic-byte detection is the only thing standing between an upload and being served
 * back from the API's own origin, so it is worth testing directly. The media module had
 * no tests at all before this.
 */
class ImageContentTypeTest {

    private static byte[] header(int... bytes) {
        byte[] header = new byte[ImageContentType.SNIFF_LENGTH];
        for (int i = 0; i < bytes.length && i < header.length; i++) {
            header[i] = (byte) bytes[i];
        }
        return header;
    }

    @Test
    void detectsJpeg() {
        Optional<ImageContentType> detected = ImageContentType.detect(header(0xFF, 0xD8, 0xFF));

        assertThat(detected).isPresent();
        assertThat(detected.get().mimeType()).isEqualTo("image/jpeg");
        // Includes the dot, so callers concatenate rather than remembering to add one.
        assertThat(detected.get().extension()).isEqualTo(".jpg");
    }

    @Test
    void detectsPng() {
        Optional<ImageContentType> detected =
            ImageContentType.detect(header(0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A));

        assertThat(detected).isPresent();
        assertThat(detected.get().mimeType()).isEqualTo("image/png");
    }

    @Test
    void detectsWebp() {
        // "RIFF" .... "WEBP" — the size field between them is not part of the signature.
        byte[] webp = header(
            'R', 'I', 'F', 'F',
            0x00, 0x00, 0x00, 0x00,
            'W', 'E', 'B', 'P'
        );

        assertThat(ImageContentType.detect(webp))
            .map(ImageContentType::mimeType)
            .contains("image/webp");
    }

    @Test
    void rejectsContentThatIsNotAPermittedImage() {
        // An HTML document, i.e. the payload that matters: served from the API origin it
        // would execute with access to whatever that origin can reach.
        assertThat(ImageContentType.detect("<!DOCTYPE html><script>".getBytes())).isEmpty();
    }

    @Test
    void rejectsAHeaderTooShortToIdentify() {
        assertThat(ImageContentType.detect(new byte[] {(byte) 0xFF})).isEmpty();
    }

    @Test
    void rejectsEmptyContent() {
        assertThat(ImageContentType.detect(new byte[0])).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"photo.jpg", "PHOTO.JPG", "a/b/photo.jpg"})
    void resolvesTheTypeFromAnExtensionItWouldHaveAssignedItself(String filename) {
        assertThat(ImageContentType.fromExtension(filename))
            .map(ImageContentType::mimeType)
            .contains("image/jpeg");
    }

    @ParameterizedTest
    @ValueSource(strings = {"photo.exe", "photo", "photo.", ".jpg.exe"})
    void refusesExtensionsItWouldNeverHaveAssigned(String filename) {
        assertThat(ImageContentType.fromExtension(filename)).isEmpty();
    }

    @Test
    void permittedMimeTypesCoversEveryConstant() {
        assertThat(ImageContentType.permittedMimeTypes())
            .hasSize(ImageContentType.values().length)
            .containsExactlyInAnyOrderElementsOf(
                java.util.Arrays.stream(ImageContentType.values()).map(ImageContentType::mimeType).toList()
            );
    }
}
