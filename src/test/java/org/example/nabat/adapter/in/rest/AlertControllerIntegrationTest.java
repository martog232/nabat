package org.example.nabat.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.nabat.adapter.out.persistence.AlertJpaRepository;
import org.example.nabat.adapter.out.persistence.UserJpaRepository;
import org.example.nabat.domain.model.AlertSeverity;
import org.example.nabat.domain.model.AlertType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AlertControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserJpaRepository userRepository;

    @Autowired
    private AlertJpaRepository alertRepository;

    @BeforeEach
    void setUp() {
        alertRepository.deleteAll();
        userRepository.deleteAll();
    }

    private AuthResponse registerAndGetAuth() throws Exception {
        RegisterRequest registerRequest = new RegisterRequest(
                "alerttest@example.com",
                "password123",
                "Alert Test User"
        );

        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isCreated())
                .andReturn();

        return objectMapper.readValue(
                result.getResponse().getContentAsString(),
                AuthResponse.class
        );
    }

    @Test
    void shouldCreateAlertWithValidToken() throws Exception {
        AuthResponse auth = registerAndGetAuth();
        String token = auth.accessToken();
        UUID userId = auth.user().id();

        CreateAlertRequest request = new CreateAlertRequest(
                "Integration Test Alert",
                "A test alert from integration test",
                AlertType.FIRE,
                AlertSeverity.HIGH,
                42.695,
                23.329
        );

        MvcResult result = mockMvc.perform(post("/api/v1/alerts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.title").value("Integration Test Alert"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn();

        // Verify the alert was created with the authenticated user's ID
        AlertResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                AlertResponse.class
        );
        org.junit.jupiter.api.Assertions.assertEquals(userId, response.reportedBy());
    }

    @Test
    void shouldReturnNearbyAlertsAfterCreation() throws Exception {
        AuthResponse auth = registerAndGetAuth();
        String token = auth.accessToken();

        CreateAlertRequest request = new CreateAlertRequest(
                "Nearby Alert",
                "A nearby alert",
                AlertType.CRIME,
                AlertSeverity.MEDIUM,
                42.695,
                23.329
        );

        mockMvc.perform(post("/api/v1/alerts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/alerts/nearby")
                        .header("Authorization", "Bearer " + token)
                        .param("latitude", "42.695")
                        .param("longitude", "23.329")
                        .param("radiusKm", "5.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].title").value("Nearby Alert"));
    }

    @Test
    void shouldReturn401WhenCreatingAlertWithoutToken() throws Exception {
        CreateAlertRequest request = new CreateAlertRequest(
                "Unauthorized Alert",
                "Should fail",
                AlertType.FIRE,
                AlertSeverity.LOW,
                42.0,
                23.0
        );

        mockMvc.perform(post("/api/v1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturn401WhenQueryingAlertsWithoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/alerts/nearby")
                        .param("latitude", "42.0")
                        .param("longitude", "23.0"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldCreateAlertWithAuthenticatedUserIdOnly() throws Exception {
        // Create two users
        AuthResponse user1 = registerAndGetAuth();
        
        // Register a second user
        RegisterRequest registerRequest2 = new RegisterRequest(
                "user2@example.com",
                "password456",
                "User Two"
        );
        MvcResult result2 = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest2)))
                .andExpect(status().isCreated())
                .andReturn();
        AuthResponse user2 = objectMapper.readValue(
                result2.getResponse().getContentAsString(),
                AuthResponse.class
        );

        // User 1 creates an alert (should be created with user 1's ID, not user 2's)
        CreateAlertRequest request = new CreateAlertRequest(
                "Security Test Alert",
                "Verifying reporter is from token, not request body",
                AlertType.OTHER,
                AlertSeverity.LOW,
                40.0,
                20.0
        );

        MvcResult alertResult = mockMvc.perform(post("/api/v1/alerts")
                        .header("Authorization", "Bearer " + user1.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        AlertResponse response = objectMapper.readValue(
                alertResult.getResponse().getContentAsString(),
                AlertResponse.class
        );

        // Verify the alert was created with user 1's ID (from the token), not user 2's
        org.junit.jupiter.api.Assertions.assertEquals(
                user1.user().id(), 
                response.reportedBy(),
                "Alert should be created with the authenticated user's ID from the JWT token"
        );
        org.junit.jupiter.api.Assertions.assertNotEquals(
                user2.user().id(), 
                response.reportedBy(),
                "Alert should NOT be created with a different user's ID"
        );
    }
}
