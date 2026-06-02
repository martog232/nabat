package org.example.nabat.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.nabat.PostgresTestSupport;
import org.example.nabat.adapter.out.persistence.UserJpaEntity;
import org.example.nabat.adapter.out.persistence.UserJpaRepository;
import org.example.nabat.application.port.out.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class UserControllerIntegrationTest extends PostgresTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserJpaRepository userRepository;

    @MockitoBean
    @SuppressWarnings("unused")
    private EmailSender emailSender;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void shouldUpdatePreferencesForAuthenticatedUser() throws Exception {
        AuthResponse authResponse = registerAndGetAuth("prefs@example.com");

        mockMvc.perform(patch("/api/v1/users/me/preferences")
                .header("Authorization", "Bearer " + authResponse.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "notificationRadiusKm": 10,
                      "lastKnownLat": 42.3601,
                      "lastKnownLng": -71.0589
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("prefs@example.com"))
            .andExpect(jsonPath("$.notificationRadiusKm").value(10))
            .andExpect(jsonPath("$.role").value("USER"));

        UserJpaEntity savedUser = userRepository.findByEmail("prefs@example.com").orElseThrow();
        assertThat(savedUser.getNotificationRadiusKm()).isEqualTo(10);
        assertThat(savedUser.getLastKnownLat()).isEqualTo(42.3601);
        assertThat(savedUser.getLastKnownLng()).isEqualTo(-71.0589);
        assertThat(savedUser.getLocationUpdatedAt()).isBeforeOrEqualTo(Instant.now());
    }

    @Test
    void shouldReturn400ForInvalidRadius() throws Exception {
        AuthResponse authResponse = registerAndGetAuth("invalid-radius@example.com");

        mockMvc.perform(patch("/api/v1/users/me/preferences")
                .header("Authorization", "Bearer " + authResponse.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "notificationRadiusKm": 3
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    private AuthResponse registerAndGetAuth(String email) throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(
            email,
            "password123",
            "Preference User"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerRequest)))
            .andExpect(status().isCreated())
            .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }
}
