package org.example.nabat.voting.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.nabat.identity.application.port.in.AuthenticateSessionUseCase;
import org.example.nabat.voting.application.port.in.VoteAlertUseCase;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.identity.domain.Role;
import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.voting.domain.VoteType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AlertVoteController.class)
@AutoConfigureMockMvc(addFilters = false)
class AlertVoteControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private VoteAlertUseCase voteAlertUseCase;

    @MockitoBean
    private AuthenticateSessionUseCase authenticateSessionUseCase;

    private static final UUID ALERT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private User buildTestUser() {
        return new User(
                UserId.of(USER_ID),
                "user@example.com",
                "encoded-password",
                "Test User",
                Role.USER,
                true,
                false,
                Instant.now(),
                Instant.now(),
                5,
                null,
                null,
                null,
                                 0
        );
    }

    private void authenticateAs(User user) {
        var auth = new UsernamePasswordAuthenticationToken(
                user, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void shouldCreateVoteWithAuthenticatedUser() throws Exception {
        User user = buildTestUser();
        authenticateAs(user);

        VoteAlertUseCase.VoteReceipt vote = new VoteAlertUseCase.VoteReceipt(
                UUID.randomUUID(),
                AlertId.of(ALERT_ID),
                VoteType.UPVOTE,
                Instant.now(),
                new VoteAlertUseCase.VoteStats(4, 1, 2, 7));
        when(voteAlertUseCase.vote(any())).thenReturn(vote);

        String requestBody = objectMapper.writeValueAsString(
                new AlertVoteController.VoteRequest(VoteType.UPVOTE)
        );

        mockMvc.perform(post("/api/v1/alerts/{alertId}/votes", ALERT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alertId").value(ALERT_ID.toString()))
                .andExpect(jsonPath("$.voteType").value("UPVOTE"))
                // Tallies come back with the vote, so the client needs no follow-up call
                // to the eventually-consistent stats endpoint.
                .andExpect(jsonPath("$.stats.upvotes").value(4))
                .andExpect(jsonPath("$.stats.credibilityScore").value(7));
    }

    @Test
    void shouldRemoveVoteAndReturnResultingStats() throws Exception {
        User user = buildTestUser();
        authenticateAs(user);

        when(voteAlertUseCase.removeVote(any(), any()))
                .thenReturn(new VoteAlertUseCase.VoteStats(3, 1, 0, 2));

        // 200 with the new tallies rather than 204, so the caller learns the new state.
        mockMvc.perform(delete("/api/v1/alerts/{alertId}/votes", ALERT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upvotes").value(3))
                .andExpect(jsonPath("$.credibilityScore").value(2));
    }

    @Test
    void shouldReturnVoteStats() throws Exception {
        // The score is supplied by the voting service, not derived here.
        VoteAlertUseCase.VoteStats stats = new VoteAlertUseCase.VoteStats(10, 2, 3, 14);
        when(voteAlertUseCase.getVoteStats(any())).thenReturn(stats);

        mockMvc.perform(get("/api/v1/alerts/{alertId}/votes/stats", ALERT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upvotes").value(10))
                .andExpect(jsonPath("$.downvotes").value(2))
                .andExpect(jsonPath("$.confirmations").value(3))
                .andExpect(jsonPath("$.credibilityScore").value(14));
    }

    @Test
    void shouldReturnWhetherUserHasVoted() throws Exception {
        User user = buildTestUser();
        authenticateAs(user);

        when(voteAlertUseCase.findUserVote(any(), any())).thenReturn(Optional.of(VoteType.UPVOTE));

        mockMvc.perform(get("/api/v1/alerts/{alertId}/votes/me", ALERT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasVoted").value(true))
                .andExpect(jsonPath("$.voteType").value("UPVOTE"));
    }

    @Test
    void shouldReturnHasVotedFalseWhenNoVote() throws Exception {
        User user = buildTestUser();
        authenticateAs(user);

        when(voteAlertUseCase.findUserVote(any(), any())).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/v1/alerts/{alertId}/votes/me", ALERT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasVoted").value(false))
                .andExpect(jsonPath("$.voteType").isEmpty());
    }
}
