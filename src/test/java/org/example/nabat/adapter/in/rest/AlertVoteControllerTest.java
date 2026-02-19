package org.example.nabat.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.nabat.adapter.in.security.JwtTokenProvider;
import org.example.nabat.application.port.in.VoteAlertUseCase;
import org.example.nabat.application.port.out.UserRepository;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.AlertVote;
import org.example.nabat.domain.model.AlertVoteId;
import org.example.nabat.domain.model.Role;
import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.domain.model.VoteType;
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
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

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
                Instant.now(),
                Instant.now()
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

        AlertVote vote = new AlertVote(
                AlertVoteId.generate(),
                AlertId.of(ALERT_ID),
                UserId.of(USER_ID),
                VoteType.UPVOTE,
                Instant.now()
        );
        when(voteAlertUseCase.vote(any())).thenReturn(vote);

        String requestBody = objectMapper.writeValueAsString(
                new AlertVoteController.VoteRequest(VoteType.UPVOTE)
        );

        mockMvc.perform(post("/api/v1/alerts/{alertId}/votes", ALERT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alertId").value(ALERT_ID.toString()))
                .andExpect(jsonPath("$.voteType").value("UPVOTE"));
    }

    @Test
    void shouldRemoveVote() throws Exception {
        User user = buildTestUser();
        authenticateAs(user);

        doNothing().when(voteAlertUseCase).removeVote(any(), any());

        mockMvc.perform(delete("/api/v1/alerts/{alertId}/votes", ALERT_ID))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturnVoteStats() throws Exception {
        VoteAlertUseCase.VoteStats stats = VoteAlertUseCase.VoteStats.of(10, 2, 3);
        when(voteAlertUseCase.getVoteStats(any())).thenReturn(stats);

        mockMvc.perform(get("/api/v1/alerts/{alertId}/votes/stats", ALERT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.upvotes").value(10))
                .andExpect(jsonPath("$.downvotes").value(2))
                .andExpect(jsonPath("$.confirmations").value(3));
    }

    @Test
    void shouldReturnWhetherUserHasVoted() throws Exception {
        User user = buildTestUser();
        authenticateAs(user);

        when(voteAlertUseCase.hasUserVoted(any(), any())).thenReturn(true);

        mockMvc.perform(get("/api/v1/alerts/{alertId}/votes/me", ALERT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasVoted").value(true));
    }
}
