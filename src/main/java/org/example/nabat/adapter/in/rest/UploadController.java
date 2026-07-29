package org.example.nabat.adapter.in.rest;

import org.example.nabat.application.port.out.FileStoragePort;
import org.example.nabat.application.port.out.FileStoragePort.StoredContent;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/**
 * Two-step photo upload: {@code POST} here, then reference the returned URL when
 * creating an alert.
 */
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private final FileStoragePort fileStoragePort;

    public UploadController(FileStoragePort fileStoragePort) {
        // No upload-dir value and no direct filesystem access: path resolution and the
        // traversal check live behind the port, where they were already implemented.
        this.fileStoragePort = fileStoragePort;
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }

        FileStoragePort.StoredFile stored = fileStoragePort.store(new FileStoragePort.FileUpload(
                file.getOriginalFilename(),
                file.getContentType(),
                file.getSize(),
                file.getInputStream()
        ));

        return ResponseEntity.ok(Map.of("url", stored.url()));
    }

    /**
     * Serves a stored photo.
     *
     * <h2>Why the headers matter</h2>
     * These files are user-supplied content served from the API's own origin, so they
     * are handed back as an attachment with {@code X-Content-Type-Options: nosniff} and
     * a restrictive CSP. Without that, a file that slipped past type validation could
     * execute as script in the API origin — where the frontend's tokens are reachable.
     * The content type is the one determined by magic-byte sniffing at write time, never
     * re-derived from the filename.
     */
    @GetMapping("/{filename}")
    public ResponseEntity<Resource> serve(@PathVariable String filename) {
        Optional<StoredContent> stored = fileStoragePort.load(filename);
        if (stored.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        StoredContent content = stored.get();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(content.contentType()))
                .contentLength(content.size())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                // Immutable: the filename is a fresh UUID for every upload.
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .body(new InputStreamResource(content.content()));
    }
}
