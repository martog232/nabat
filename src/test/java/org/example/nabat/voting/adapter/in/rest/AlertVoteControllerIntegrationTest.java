package org.example.nabat.voting.adapter.in.rest;

import org.example.nabat.PostgresTestSupport;
import tools.jackson.databind.ObjectMapper;
import org.example.nabat.incident.adapter.in.rest.AlertResponse;
import org.example.nabat.identity.adapter.in.rest.AuthResponse;
import org.example.nabat.incident.adapter.in.rest.CreateAlertRequest;
import org.example.nabat.identity.adapter.in.rest.RegisterRequest;
import org.example.nabat.identity.adapter.out.persistence.UserJpaRepository;
import org.example.nabat.identity.application.port.out.EmailSender;
import org.example.nabat.voting.application.port.out.ExternalVotingPort;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.voting.domain.VoteType;
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

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers(disabledWithoutDocker = true)
class AlertVoteControllerIntegrationTest extends PostgresTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserJpaRepository userRepository;

    /** Prevent real SMTP during integration tests. */
    @MockitoBean
    private EmailSender emailSender;

    @MockitoBean
    private ExternalVotingPort externalVotingPort;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    void voteStatsSwitchRemoveAndDuplicateRemoveConflict_happyPath() throws Exception {
        AuthResponse auth = register("vote-integration@example.com", "Vote Integration User");
        UUID alertId = createAlert(auth.accessToken());
        UUID voteId = UUID.randomUUID();

        when(externalVotingPort.vote(any(), any(), any())).thenReturn(new ExternalVotingPort.VoteResult(
                voteId,
                AlertId.of(alertId),
                VoteType.CONFIRM,
                Instant.now(),
                // Tallies now travel back with the vote rather than being fetched afterwards.
                new ExternalVotingPort.VoteStats(1, 0, 0, 1)
        ));
        when(externalVotingPort.getVoteStats(any())).thenReturn(new ExternalVotingPort.VoteStats(1, 0, 0, 1));
        when(externalVotingPort.findUserVote(any(), any())).thenReturn(Optional.of(VoteType.CONFIRM));
        when(externalVotingPort.removeVote(any(AlertId.class), any(UserId.class)))
                .thenReturn(new ExternalVotingPort.VoteStats(0, 0, 0, 0))
                .thenThrow(new IllegalStateException("No existing vote to remove."));

        // 1) create vote (CONFIRM)
        mockMvc.perform(post("/api/v1/alerts/{alertId}/votes", alertId)
                .header("Authorization", "Bearer " + auth.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AlertVoteController.VoteRequest(VoteType.CONFIRM))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.alertId").value(alertId.toString()))
            .andExpect(jsonPath("$.voteType").value("CONFIRM"));

        // 2) stats endpoint still reads from voting service
        mockMvc.perform(get("/api/v1/alerts/{alertId}/votes/stats", alertId)
                        .header("Authorization", "Bearer " + auth.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upvotes").value(1))
                .andExpect(jsonPath("$.downvotes").value(0))
                .andExpect(jsonPath("$.confirmations").value(0));

        // 3) user vote endpoint still delegates to voting service
        mockMvc.perform(get("/api/v1/alerts/{alertId}/votes/me", alertId)
                        .header("Authorization", "Bearer " + auth.accessToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasVoted").value(true))
                .andExpect(jsonPath("$.voteType").value("CONFIRM"));

        // 4) remove vote — 200 with the resulting tallies, not 204
        mockMvc.perform(delete("/api/v1/alerts/{alertId}/votes", alertId)
                .header("Authorization", "Bearer " + auth.accessToken()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.upvotes").value(0));

        // 5) duplicate remove still returns conflict
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
            1028,
            42.6977,
            23.3219,
            null
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
