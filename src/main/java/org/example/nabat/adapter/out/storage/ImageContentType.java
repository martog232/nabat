package org.example.nabat.adapter.out.storage;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * The image formats alert photos may be uploaded in, identified by their magic bytes.
 *
 * <h2>Why sniff instead of trusting the client</h2>
 * Both the filename extension and the {@code Content-Type} header are attacker
 * controlled. Storage previously took the extension straight from
 * {@code MultipartFile.getOriginalFilename()} with no check at all, and the serving
 * endpoint handed the file back with a type derived from that extension — so
 * uploading {@code payload.html} or an SVG containing a {@code <script>} produced a
 * stored cross-site-scripting payload served from the API's own origin, against a
 * frontend that keeps its tokens in {@code localStorage}.
 *
 * <p>SVG is deliberately <em>not</em> accepted: it is an XML document that can carry
 * script, so it cannot be served inline safely.
 */
public enum ImageContentType {

    JPEG("image/jpeg", ".jpg", new int[]{0xFF, 0xD8, 0xFF}),
    PNG("image/png", ".png", new int[]{0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A}),
    GIF("image/gif", ".gif", new int[]{0x47, 0x49, 0x46, 0x38}),
    /** RIFF....WEBP — bytes 8-11 identify it, so only the RIFF prefix is matched here. */
    WEBP("image/webp", ".webp", new int[]{0x52, 0x49, 0x46, 0x46});

    /** Enough bytes to cover the longest signature above. */
    public static final int SNIFF_LENGTH = 12;

    private final String mimeType;
    private final String extension;
    private final int[] signature;

    ImageContentType(String mimeType, String extension, int[] signature) {
        this.mimeType = mimeType;
        this.extension = extension;
        this.signature = signature;
    }

    public String mimeType() {
        return mimeType;
    }

    /** Canonical extension — the client's is discarded. */
    public String extension() {
        return extension;
    }

    /** Identifies the format from the leading bytes of the file. */
    public static Optional<ImageContentType> detect(byte[] header) {
        return Arrays.stream(values())
            .filter(candidate -> candidate.matches(header))
            .findFirst();
    }

    /** Looks a stored file's type up from the extension this enum itself assigned. */
    public static Optional<ImageContentType> fromExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot < 0) {
            return Optional.empty();
        }
        String ext = filename.substring(dot).toLowerCase(java.util.Locale.ROOT);
        return Arrays.stream(values())
            .filter(candidate -> candidate.extension.equals(ext))
            .findFirst();
    }

    public static List<String> permittedMimeTypes() {
        return Arrays.stream(values()).map(ImageContentType::mimeType).toList();
    }

    private boolean matches(byte[] header) {
        if (header.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((header[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
