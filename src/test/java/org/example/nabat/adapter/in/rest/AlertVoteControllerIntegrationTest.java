package org.example.nabat.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.nabat.adapter.in.security.RateLimitingFilter;
import org.example.nabat.adapter.out.persistence.UserJpaRepository;
import org.example.nabat.application.port.out.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AlertVoteControllerIntegrationTest extends PostgisSpringBootIntegrationTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserJpaRepository userRepository;

    /** Prevent real SMTP during integration tests. */
    @MockBean
    private EmailSender emailSender;

    @Autowired
    private RateLimitingFilter rateLimitingFilter;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        rateLimitingFilter.resetBuckets();
    }

    @Test
    void voteStatsSwitchRemoveAndDuplicateRemoveConflict_happyPath() throws Exception {
        AuthResponse auth = register("vote-integration@example.com", "Vote Integration User");
        UUID alertId = createAlert(auth.accessToken());

        // 1) create vote (UPVOTE)
        mockMvc.perform(post("/api/v1/alerts/{alertId}/votes", alertId)
                .header("Authorization", "Bearer " + auth.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AlertVoteController.VoteRequest(org.example.nabat.domain.model.VoteType.UPVOTE))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.alertId").value(alertId.toString()))
            .andExpect(jsonPath("$.voteType").value("UPVOTE"));

        // 2) stats after upvote
        mockMvc.perform(get("/api/v1/alerts/{alertId}/votes/stats", alertId)
                .header("Authorization", "Bearer " + auth.accessToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.upvotes").value(1))
            .andExpect(jsonPath("$.downvotes").value(0))
            .andExpect(jsonPath("$.confirmations").value(0));

        // 3) switch vote to DOWNVOTE
        mockMvc.perform(post("/api/v1/alerts/{alertId}/votes", alertId)
                .header("Authorization", "Bearer " + auth.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AlertVoteController.VoteRequest(org.example.nabat.domain.model.VoteType.DOWNVOTE))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.alertId").value(alertId.toString()))
            .andExpect(jsonPath("$.voteType").value("DOWNVOTE"));

        mockMvc.perform(get("/api/v1/alerts/{alertId}/votes/stats", alertId)
                .header("Authorization", "Bearer " + auth.accessToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.upvotes").value(0))
            .andExpect(jsonPath("$.downvotes").value(1))
            .andExpect(jsonPath("$.confirmations").value(0));

        // 4) remove vote
        mockMvc.perform(delete("/api/v1/alerts/{alertId}/votes", alertId)
                .header("Authorization", "Bearer " + auth.accessToken()))
            .andExpect(status().isNoContent());

        // 5) duplicate remove -> 409 conflict
        mockMvc.perform(delete("/api/v1/alerts/{alertId}/votes", alertId)
                .header("Authorization", "Bearer " + auth.accessToken()))
            .andExpect(status().isConflict());
    }

    private AuthResponse register(String email, String displayName) throws Exception {
        RegisterRequest request = new RegisterRequest(email, "password123", displayName);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        return objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
    }

    private UUID createAlert(String accessToken) throws Exception {
        CreateAlertRequest request = new CreateAlertRequest(
            "Road blocked",
            "Road partially blocked by debris",
            org.example.nabat.domain.model.AlertType.ACCIDENT,
            org.example.nabat.domain.model.AlertSeverity.MEDIUM,
            42.6977,
            23.3219
        );

        MvcResult result = mockMvc.perform(post("/api/v1/alerts")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andReturn();

        AlertResponse response = objectMapper.readValue(result.getResponse().getContentAsString(), AlertResponse.class);
        return response.id();
    }
}

