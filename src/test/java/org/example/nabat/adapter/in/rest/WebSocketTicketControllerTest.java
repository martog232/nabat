package org.example.nabat.adapter.in.rest;

import org.example.nabat.adapter.in.security.JwtTokenProvider;
import org.example.nabat.application.port.in.IssueWebSocketTicketUseCase;
import org.example.nabat.application.port.out.UserRepository;
import org.example.nabat.domain.model.Role;
import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(WebSocketTicketController.class)
@AutoConfigureMockMvc(addFilters = false)
class WebSocketTicketControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IssueWebSocketTicketUseCase issueWebSocketTicketUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    @Test
    void issuesTicketForAuthenticatedUser() throws Exception {
        Instant expiresAt = Instant.parse("2026-05-18T12:00:00Z");
        when(issueWebSocketTicketUseCase.issueTicket(any()))
            .thenReturn(new IssueWebSocketTicketUseCase.IssuedWebSocketTicket("ticket-123", expiresAt));

        Instant now = Instant.now();
        User mockUser = new User(
            UserId.of(UUID.fromString("00000000-0000-0000-0000-000000000111")),
            "ws@example.com",
            "hashedpass",
            "WebSocket User",
            Role.USER,
            true,
            true,
            now,
            now,
            5,
            null,
            null,
            null
        );

        mockMvc.perform(post("/api/v1/ws/tickets")
                .with(request -> {
                    var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                        mockUser, null, List.of()
                    );
                    org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
                    return request;
                }))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.ticket").value("ticket-123"))
            .andExpect(jsonPath("$.expiresAt").value("2026-05-18T12:00:00Z"));
    }

    @Test
    void returns401WhenPrincipalMissing() throws Exception {
        mockMvc.perform(post("/api/v1/ws/tickets"))
            .andExpect(status().isUnauthorized());
    }
}
