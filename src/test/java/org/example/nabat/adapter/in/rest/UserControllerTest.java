package org.example.nabat.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.nabat.adapter.in.security.JwtTokenProvider;
import org.example.nabat.application.port.in.UpdateUserPreferencesUseCase;
import org.example.nabat.application.port.out.UserRepository;
import org.example.nabat.domain.model.Role;
import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.UserId;
import org.junit.jupiter.api.BeforeEach;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private UpdateUserPreferencesUseCase updateUserPreferencesUseCase;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    private User user;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now();
        user = new User(
            UserId.generate(),
            "user@example.com",
            "hash",
            "Test User",
            Role.USER,
            true,
            true,
            now.minusSeconds(60),
            now,
            5,
            42.1,
            23.1,
            now
        );
        var auth = new UsernamePasswordAuthenticationToken(
            user,
            null,
            List.of(new SimpleGrantedAuthority("ROLE_USER"))
        );
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    void shouldUpdatePreferences() throws Exception {
        User updatedUser = user.withLocation(42.3601, -71.0589, 10);
        when(updateUserPreferencesUseCase.updatePreferences(any())).thenReturn(updatedUser);

        mockMvc.perform(patch("/api/v1/users/me/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new UserController.UpdateUserPreferencesRequest(10, 42.3601, -71.0589)
                )))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("user@example.com"))
            .andExpect(jsonPath("$.notificationRadiusKm").value(10));
    }

    @Test
    void shouldReturn400ForInvalidRadius() throws Exception {
        mockMvc.perform(patch("/api/v1/users/me/preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "notificationRadiusKm": 3
                    }
                    """))
            .andExpect(status().isBadRequest());
    }
}
