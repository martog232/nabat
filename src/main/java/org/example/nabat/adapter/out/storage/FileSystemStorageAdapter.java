package org.example.nabat.adapter.out.storage;

import org.example.nabat.application.port.out.FileStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Component
public class FileSystemStorageAdapter implements FileStoragePort {

    private static final Logger log = LoggerFactory.getLogger(FileSystemStorageAdapter.class);

    private final Path uploadDir;
    private final String servePath;

    public FileSystemStorageAdapter(
            @Value("${nabat.storage.upload-dir}") String uploadDir,
            @Value("${nabat.storage.serve-path}") String servePath
    ) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        this.servePath = servePath;
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory: " + this.uploadDir, e);
        }
        log.info("File storage initialized: dir={}, servePath={}", this.uploadDir, this.servePath);
    }

    @Override
    public String store(MultipartFile file) {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot store empty file");
        }

        String originalFilename = file.getOriginalFilename();
        String ext = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            ext = originalFilename.substring(originalFilename.lastIndexOf('.'));
        }
        String filename = UUID.randomUUID() + ext;

        try {
            Path targetPath = uploadDir.resolve(filename).normalize();
            if (!targetPath.startsWith(uploadDir)) {
                throw new SecurityException("Filename traversal detected: " + filename);
            }
            Files.copy(file.getInputStream(), targetPath);
            log.info("Stored file: {} -> {}", originalFilename, targetPath);
            return servePath + filename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file: " + originalFilename, e);
        }
    }
}
