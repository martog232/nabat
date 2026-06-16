package org.example.nabat.application.port.out;

import org.springframework.web.multipart.MultipartFile;

public interface FileStoragePort {

    /**
     * Store an uploaded file and return the relative URL path that can be used
     * to retrieve it (e.g. "/api/v1/uploads/abc-123.jpg").
     */
    String store(MultipartFile file);
}
