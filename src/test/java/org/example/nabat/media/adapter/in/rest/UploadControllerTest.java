package org.example.nabat.media.adapter.in.rest;

import org.example.nabat.identity.application.port.in.AuthenticateSessionUseCase;
import org.example.nabat.media.application.port.in.LoadPhotoUseCase;
import org.example.nabat.media.application.port.in.StorePhotoUseCase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The upload endpoints had no test of any kind. The response headers on {@code GET} are
 * the part that matters most: these are user-supplied bytes served from the API's own
 * origin, so losing {@code nosniff}, the attachment disposition or the CSP turns an
 * upload into stored XSS against the origin the frontend's tokens live on.
 */
@WebMvcTest(UploadController.class)
@AutoConfigureMockMvc(addFilters = false)
class UploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StorePhotoUseCase storePhoto;

    @MockitoBean
    private LoadPhotoUseCase loadPhoto;

    // JwtAuthenticationFilter is a @Component and a Filter, so @WebMvcTest instantiates it
    // even with addFilters = false. Its collaborators have to exist for the context to start.
    @MockitoBean
    private AuthenticateSessionUseCase authenticateSessionUseCase;

    @Test
    void returnsTheUrlOfAStoredUpload() throws Exception {
        when(storePhoto.store(any())).thenReturn(new StorePhotoUseCase.StoredPhoto("/api/v1/uploads/abc.jpg"));

        MockMultipartFile file =
            new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[] {1, 2, 3});

        mockMvc.perform(multipart("/api/v1/uploads").file(file))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.url").value("/api/v1/uploads/abc.jpg"));
    }

    @Test
    void rejectsAnEmptyPartWithoutTouchingStorage() throws Exception {
        MockMultipartFile empty = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[0]);

        mockMvc.perform(multipart("/api/v1/uploads").file(empty))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("File is empty"));

        verify(storePhoto, never()).store(any());
    }

    @Test
    void servesAStoredPhotoWithTheHardeningHeaders() throws Exception {
        InputStream bytes = new ByteArrayInputStream(new byte[] {4, 5, 6});
        when(loadPhoto.load("abc.jpg"))
            .thenReturn(Optional.of(new LoadPhotoUseCase.PhotoContent("image/jpeg", 3L, bytes)));

        mockMvc.perform(get("/api/v1/uploads/abc.jpg"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "image/jpeg"))
            .andExpect(header().longValue(HttpHeaders.CONTENT_LENGTH, 3L))
            .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"abc.jpg\""))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Content-Security-Policy", "default-src 'none'; sandbox"))
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "max-age=31536000, public, immutable"))
            .andExpect(content().bytes(new byte[] {4, 5, 6}));
    }

    @Test
    void returns404ForAPhotoThatDoesNotExist() throws Exception {
        when(loadPhoto.load("gone.jpg")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/uploads/gone.jpg"))
            .andExpect(status().isNotFound());
    }
}
