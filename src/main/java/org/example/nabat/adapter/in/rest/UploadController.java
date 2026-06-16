package org.example.nabat.adapter.in.rest;

import org.example.nabat.application.port.out.FileStoragePort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private final FileStoragePort fileStoragePort;
    private final Path uploadDir;

    public UploadController(
            FileStoragePort fileStoragePort,
            @org.springframework.beans.factory.annotation.Value("${nabat.storage.upload-dir}") String uploadDir
    ) {
        this.fileStoragePort = fileStoragePort;
        this.uploadDir = Path.of(uploadDir).toAbsolutePath().normalize();
    }

    @PostMapping
    public ResponseEntity<Map<String, String>> upload(
            @RequestParam("file") MultipartFile file
    ) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
        }
        String url = fileStoragePort.store(file);
        return ResponseEntity.ok(Map.of("url", url));
    }

    @GetMapping("/{filename}")
    public ResponseEntity<org.springframework.core.io.Resource> serve(@PathVariable String filename) {
        try {
            Path filePath = uploadDir.resolve(filename).normalize();
            if (!filePath.startsWith(uploadDir)) {
                return ResponseEntity.badRequest().build();
            }
            if (!Files.exists(filePath)) {
                return ResponseEntity.notFound().build();
            }
            org.springframework.core.io.Resource resource = new org.springframework.core.io.UrlResource(filePath.toUri());
            String contentType = Files.probeContentType(filePath);
            if (contentType == null) {
                contentType = "application/octet-stream";
            }
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .body(resource);
        } catch (Exception e) {
            return ResponseEntity.notFound().build();
        }
    }
}
