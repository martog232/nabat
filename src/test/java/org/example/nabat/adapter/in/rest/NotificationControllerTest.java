package org.example.nabat.adapter.in.rest;

import org.example.nabat.adapter.in.security.JwtTokenProvider;
import org.example.nabat.application.port.in.GetNotificationUseCase;
import org.example.nabat.application.port.out.UserRepository;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.Notification;
import org.example.nabat.domain.model.NotificationId;
import org.example.nabat.domain.model.NotificationType;
import org.example.nabat.domain.model.Role;
import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
@AutoConfigureMockMvc(addFilters = false)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetNotificationUseCase useCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User(UserId.generate(), "u@x.y", "p", "n",
                Role.USER, true, Instant.now(), Instant.now());
        var auth = new UsernamePasswordAuthenticationToken(user, null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private Notification sample() {
        return new Notification(NotificationId.generate(), user.id(),
                NotificationType.ALERT_UPVOTED, "t", "m",
                AlertId.generate(), null, false, Instant.now());
    }

    @Test
    void list_returns200() throws Exception {
        when(useCase.getNotifications(user.id())).thenReturn(List.of(sample()));
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("t"));
    }

    @Test
    void unread_returns200() throws Exception {
        when(useCase.getUnreadNotifications(user.id())).thenReturn(List.of(sample()));
        mockMvc.perform(get("/api/v1/notifications/unread"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void unreadCount_returnsCount() throws Exception {
        when(useCase.countUnreadNotifications(user.id())).thenReturn(5);
        mockMvc.perform(get("/api/v1/notifications/unread/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(5));
    }

    @Test
    void markRead_returns200() throws Exception {
        Notification n = sample();
        when(useCase.markAsRead(eq(n.id()), eq(user.id()))).thenReturn(n);
        mockMvc.perform(post("/api/v1/notifications/{id}/read", n.id().value()))
                .andExpect(status().isOk());
    }

    @Test
    void markAll_returns204() throws Exception {
        doNothing().when(useCase).markAllAsRead(user.id());
        mockMvc.perform(post("/api/v1/notifications/read-all"))
                .andExpect(status().isNoContent());
    }

    @Test
    void markRead_unknownId_returns400() throws Exception {
        UUID id = UUID.randomUUID();
        when(useCase.markAsRead(any(), any()))
                .thenThrow(new IllegalArgumentException("not found"));
        mockMvc.perform(post("/api/v1/notifications/{id}/read", id))
                .andExpect(status().isBadRequest());
    }
}

