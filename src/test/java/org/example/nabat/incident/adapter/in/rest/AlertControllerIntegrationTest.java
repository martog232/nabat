package org.example.nabat.incident.adapter.in.rest;

import org.example.nabat.identity.adapter.in.rest.RegisterRequest;
import org.example.nabat.PostgresTestSupport;
import org.example.nabat.identity.adapter.in.rest.AuthResponse;
import tools.jackson.databind.ObjectMapper;
import org.example.nabat.incident.adapter.out.persistence.AlertJpaRepository;
import org.example.nabat.identity.adapter.out.persistence.UserJpaRepository;
import org.example.nabat.identity.application.port.out.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AlertControllerIntegrationTest extends PostgresTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    /** Prevent real SMTP during integration tests. */
    @MockitoBean
    private EmailSender emailSender;

    @Autowired
    private UserJpaRepository userRepository;

    @Autowired
    private AlertJpaRepository alertRepository;

    @BeforeEach
    void setUp() {
        alertRepository.deleteAllInBatch();
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
                1007,
                42.695,
                23.329,
                null
        );

        MvcResult result = mockMvc.perform(post("/api/v1/alerts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(notNullValue()))
                .andExpect(jsonPath("$.title").value("Integration Test Alert"))
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.districtCode").value(notNullValue()))
                .andReturn();

        // Verify the alert was created with the authenticated user's ID
        AlertResponse response = objectMapper.readValue(
                result.getResponse().getContentAsString(),
                AlertResponse.class
        );
        org.junit.jupiter.api.Assertions.assertEquals(userId, response.reportedBy());
    }

    /** The public list and the district names it is shown with need no session. */
    @Test
    void thePublicListIsReadableWithoutSigningIn() throws Exception {
        mockMvc.perform(get("/api/v1/alerts/ranked").param("ranking", "LONGEST_WAITING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ranking").value("LONGEST_WAITING"))
                .andExpect(jsonPath("$.total").value(0));
        mockMvc.perform(get("/api/v1/districts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(24)));
    }

    /** Public means readable, not open: nothing else became reachable without a token. */
    @Test
    void theRestOfTheReportApiStillNeedsASession() throws Exception {
        mockMvc.perform(get("/api/v1/alerts/nearby").param("latitude", "42.69").param("longitude", "23.32"))
                .andExpect(status().isUnauthorized());
    }

    /**
     * Plovdiv: well-formed, and nowhere nabat can send a report. 422 with its own code, so
     * a client can say "outside Sofia" rather than "invalid request" — and nothing stored.
     */
    @Test
    void refusesAReportOutsideSofia() throws Exception {
        String token = registerAndGetAuth().accessToken();
        long before = alertRepository.count();

        CreateAlertRequest request = new CreateAlertRequest(
                "Pothole in Plovdiv", "Not ours to route", 1007,
                42.1354, 24.7453, null
        );

        mockMvc.perform(post("/api/v1/alerts")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("OUTSIDE_SERVICE_AREA"));

        org.junit.jupiter.api.Assertions.assertEquals(before, alertRepository.count());
    }
    @Test
    void shouldReturnNearbyAlertsAfterCreation() throws Exception {
        AuthResponse auth = registerAndGetAuth();
        String token = auth.accessToken();

        CreateAlertRequest request = new CreateAlertRequest(
                "Nearby Alert",
                "A nearby alert",
                1001,
                42.695,
                23.329,
                null
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
                .andExpect(jsonPath("$.alerts", hasSize(1)))
                .andExpect(jsonPath("$.alerts[0].title").value("Nearby Alert"))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    void shouldReturn401WhenCreatingAlertWithoutToken() throws Exception {
        CreateAlertRequest request = new CreateAlertRequest(
                "Unauthorized Alert",
                "Should fail",
                1007,
                42.0,
                23.0,
                null
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
                1051,
                42.6515,
                23.3455,
                null
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
