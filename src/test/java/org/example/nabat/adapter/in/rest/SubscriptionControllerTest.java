package org.example.nabat.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.nabat.adapter.in.security.JwtTokenProvider;
import org.example.nabat.application.port.in.SubscribeToAlertsUseCase;
import org.example.nabat.application.port.out.UserRepository;
import org.example.nabat.domain.model.AlertType;
import org.example.nabat.domain.model.Location;
import org.example.nabat.domain.model.Role;
import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.domain.model.UserSubscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SubscriptionController.class)
@AutoConfigureMockMvc(addFilters = false)
class SubscriptionControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SubscribeToAlertsUseCase useCase;
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

    private UserSubscription sample(UUID id) {
        return new UserSubscription(id, user.id(), AlertType.FIRE,
                Location.of(42.0, 23.0), 5.0, true, Instant.now());
    }

    @Test
    void list_returns200() throws Exception {
        when(useCase.listMine(user.id())).thenReturn(List.of(sample(UUID.randomUUID())));
        mockMvc.perform(get("/api/v1/subscriptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].alertType").value("FIRE"));
    }

    @Test
    void create_returns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(useCase.subscribe(any())).thenReturn(sample(id));

        var req = new SubscriptionController.SubscriptionRequest(
                AlertType.FIRE, 42.0, 23.0, 5.0);

        mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @Test
    void create_invalid_returns400() throws Exception {
        String invalid = "{\"alertType\":\"FIRE\",\"latitude\":1,\"longitude\":2}"; // missing radiusKm
        mockMvc.perform(post("/api/v1/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalid))
                .andExpect(status().isBadRequest());
    }

    @Test
    void delete_returns204() throws Exception {
        UUID id = UUID.randomUUID();
        doNothing().when(useCase).unsubscribe(id, user.id());
        mockMvc.perform(delete("/api/v1/subscriptions/{id}", id))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_byNonOwner_returns403() throws Exception {
        UUID id = UUID.randomUUID();
        doThrow(new AccessDeniedException("nope")).when(useCase).unsubscribe(id, user.id());
        mockMvc.perform(delete("/api/v1/subscriptions/{id}", id))
                .andExpect(status().isForbidden());
    }
}

