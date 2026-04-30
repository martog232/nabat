package org.example.nabat.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.nabat.adapter.in.security.JwtTokenProvider;
import org.example.nabat.application.port.in.CreateAlertUseCase;
import org.example.nabat.application.port.in.GetAlertByIdUseCase;
import org.example.nabat.application.port.in.GetNearbyAlertsUseCase;
import org.example.nabat.application.port.in.ResolveAlertUseCase;
import org.example.nabat.application.port.out.AlertRepository;
import org.example.nabat.application.port.out.UserRepository;
import org.example.nabat.domain.model.Alert;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.AlertSeverity;
import org.example.nabat.domain.model.AlertStatus;
import org.example.nabat.domain.model.AlertType;
import org.example.nabat.domain.model.Location;
import org.example.nabat.domain.model.Role;
import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.UserId;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AlertController.class)
@AutoConfigureMockMvc(addFilters = false)
class AlertControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private CreateAlertUseCase createAlertUseCase;

    @MockitoBean
    private GetNearbyAlertsUseCase getNearbyAlertsUseCase;

    @MockitoBean
    private GetAlertByIdUseCase getAlertByIdUseCase;

    @MockitoBean
    private ResolveAlertUseCase resolveAlertUseCase;

    @MockitoBean
    private AlertRepository alertRepository;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private UserRepository userRepository;

    private Alert buildAlert() {
        return new Alert(
                AlertId.of(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                "Test Alert",
                "Test description",
                AlertType.FIRE,
                AlertSeverity.HIGH,
                Location.of(42.0, 23.0),
                Instant.parse("2024-01-01T00:00:00Z"),
                AlertStatus.ACTIVE,
                UUID.fromString("00000000-0000-0000-0000-000000000002"),
                0, 0, 0,
                null
        );
    }

    @Test
    void shouldCreateAlertAndReturn201() throws Exception {
        Alert alert = buildAlert();
        when(createAlertUseCase.createAlert(any())).thenReturn(alert);

        // Create a mock user principal
        Instant now = Instant.now();
        User mockUser = new User(
                UserId.of(UUID.fromString("00000000-0000-0000-0000-000000000002")),
                "test@example.com",
                "hashedpass",
                "Test User",
                Role.USER,
                true,
                now,
                now
        );

        CreateAlertRequest request = new CreateAlertRequest(
                "Test Alert",
                "Test description",
                AlertType.FIRE,
                AlertSeverity.HIGH,
                42.0,
                23.0
        );

        mockMvc.perform(post("/api/v1/alerts")
                        .with(user("test@example.com").password("pass").roles("USER"))
                        .with(request1 -> {
                            // Inject our domain User as the principal for @AuthenticationPrincipal
                            var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                                mockUser, "pass", java.util.Collections.emptyList()
                            );
                            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);
                            return request1;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("00000000-0000-0000-0000-000000000001"))
                .andExpect(jsonPath("$.title").value("Test Alert"))
                .andExpect(jsonPath("$.type").value("FIRE"))
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void shouldReturnNearbyAlerts() throws Exception {
        Alert alert = buildAlert();
        when(getNearbyAlertsUseCase.getNearbyAlerts(any())).thenReturn(List.of(alert));

        mockMvc.perform(get("/api/v1/alerts/nearby")
                        .param("latitude", "42.0")
                        .param("longitude", "23.0")
                        .param("radiusKm", "5.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Test Alert"));
    }

    @Test
    void shouldUseDefaultRadiusOf5WhenNotSpecified() throws Exception {
        when(getNearbyAlertsUseCase.getNearbyAlerts(any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/alerts/nearby")
                        .param("latitude", "42.0")
                        .param("longitude", "23.0"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldReturn400ForInvalidRequestBody() throws Exception {
        String invalidRequest = """
                {
                    "title": "",
                    "description": "desc",
                    "type": "FIRE",
                    "severity": "HIGH",
                    "latitude": 42.0,
                    "longitude": 23.0
                }
                """;

        mockMvc.perform(post("/api/v1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getById_returnsAlert() throws Exception {
        Alert alert = buildAlert();
        when(getAlertByIdUseCase.getById(any())).thenReturn(alert);

        mockMvc.perform(get("/api/v1/alerts/{id}", alert.id().value()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(alert.id().value().toString()));
    }

    @Test
    void getById_missing_returns404() throws Exception {
        when(getAlertByIdUseCase.getById(any()))
                .thenThrow(new org.example.nabat.domain.exception.AlertNotFoundException(
                        org.example.nabat.domain.model.AlertId.generate()));

        mockMvc.perform(get("/api/v1/alerts/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    void resolve_returns200() throws Exception {
        Alert resolved = buildAlert();
        when(resolveAlertUseCase.resolve(any(), any())).thenReturn(resolved);

        Instant now = Instant.now();
        User mockUser = new User(
                UserId.of(UUID.randomUUID()),
                "test@example.com", "p", "n",
                Role.USER, true, now, now);
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                mockUser, null, java.util.List.of());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(post("/api/v1/alerts/{id}/resolve", resolved.id().value()))
                .andExpect(status().isOk());
    }
}
