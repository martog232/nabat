package org.example.nabat.voting.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.nabat.adapter.in.rest.AlertResponse;
import org.example.nabat.adapter.in.rest.AuthResponse;
import org.example.nabat.adapter.in.rest.CreateAlertRequest;
import org.example.nabat.adapter.in.rest.PostgisSpringBootIntegrationTestSupport;
import org.example.nabat.adapter.in.rest.RegisterRequest;
import org.example.nabat.adapter.out.persistence.UserJpaRepository;
import org.example.nabat.application.port.out.EmailSender;
import org.example.nabat.voting.domain.model.VoteType;
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

import java.time.Duration;
import java.time.Instant;
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

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void voteStatsSwitchRemoveAndDuplicateRemoveConflict_happyPath() throws Exception {
        AuthResponse auth = register("vote-integration@example.com", "Vote Integration User");
        UUID alertId = createAlert(auth.accessToken());

        // 1) create vote (UPVOTE)
        mockMvc.perform(post("/api/v1/alerts/{alertId}/votes", alertId)
                .header("Authorization", "Bearer " + auth.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AlertVoteController.VoteRequest(VoteType.UPVOTE))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.alertId").value(alertId.toString()))
            .andExpect(jsonPath("$.voteType").value("UPVOTE"));

        // 2) stats after upvote (eventual consistency: projection updated asynchronously)
        awaitStats(auth.accessToken(), alertId, 1, 0, 0);

        // 3) switch vote to DOWNVOTE
        mockMvc.perform(post("/api/v1/alerts/{alertId}/votes", alertId)
                .header("Authorization", "Bearer " + auth.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AlertVoteController.VoteRequest(VoteType.DOWNVOTE))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.alertId").value(alertId.toString()))
            .andExpect(jsonPath("$.voteType").value("DOWNVOTE"));

        awaitStats(auth.accessToken(), alertId, 0, 1, 0);

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

    private void awaitStats(String accessToken, UUID alertId, int expectedUpvotes, int expectedDownvotes, int expectedConfirmations)
            throws Exception {
        Instant timeoutAt = Instant.now().plus(Duration.ofSeconds(3));
        AssertionError lastAssertionError = null;

        while (Instant.now().isBefore(timeoutAt)) {
            try {
                mockMvc.perform(get("/api/v1/alerts/{alertId}/votes/stats", alertId)
                                .header("Authorization", "Bearer " + accessToken))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.upvotes").value(expectedUpvotes))
                        .andExpect(jsonPath("$.downvotes").value(expectedDownvotes))
                        .andExpect(jsonPath("$.confirmations").value(expectedConfirmations));
                return;
            } catch (AssertionError ex) {
                lastAssertionError = ex;
                Thread.sleep(75);
            }
        }

        if (lastAssertionError != null) {
            throw lastAssertionError;
        }
    }
}
