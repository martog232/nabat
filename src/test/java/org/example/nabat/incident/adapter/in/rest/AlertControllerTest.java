package org.example.nabat.incident.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.nabat.identity.adapter.in.security.JwtTokenProvider;
import org.example.nabat.incident.application.port.in.CreateAlertUseCase;
import org.example.nabat.incident.application.port.in.GetAlertByIdUseCase;
import org.example.nabat.incident.application.port.in.GetNearbyAlertsUseCase;
import org.example.nabat.incident.application.port.in.ResolveAlertUseCase;
import org.example.nabat.incident.application.port.in.ListAlertsUseCase;
import org.example.nabat.identity.application.port.out.UserRepository;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertSeverity;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.AlertType;
import org.example.nabat.shared.domain.Location;
import org.example.nabat.identity.domain.Role;
import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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

    /**
     * Replaces the {@code AlertRepository} out-port this test used to mock: the
     * controller now depends on an in-port for the admin listing instead of reaching
     * into the repository directly.
     */
    @MockitoBean
    private ListAlertsUseCase listAlertsUseCase;

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

                0,
                null,
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
                false,
                now,
                now,
                5,
                null,
                null,
                null
        ,

                0);

        CreateAlertRequest request = new CreateAlertRequest(
                "Test Alert",
                "Test description",
                AlertType.FIRE,
                AlertSeverity.HIGH,
                42.0,
                23.0,
                null
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
                .andExpect(jsonPath("$.alerts[0].title").value("Test Alert"))
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.limit").value(100))
                .andExpect(jsonPath("$.truncated").value(false));
    }

    @Test
    void shouldReportTruncationWhenTheLimitIsReached() throws Exception {
        when(getNearbyAlertsUseCase.getNearbyAlerts(any()))
            .thenReturn(List.of(buildAlert(), buildAlert()));

        mockMvc.perform(get("/api/v1/alerts/nearby")
                        .param("latitude", "42.0")
                        .param("longitude", "23.0")
                        .param("limit", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.truncated").value(true));
    }

    @Test
    void shouldPassFiltersAndLimitToTheUseCase() throws Exception {
        when(getNearbyAlertsUseCase.getNearbyAlerts(any())).thenReturn(Collections.emptyList());

        mockMvc.perform(get("/api/v1/alerts/nearby")
                        .param("latitude", "42.0")
                        .param("longitude", "23.0")
                        .param("type", "FIRE")
                        .param("severity", "CRITICAL")
                        .param("limit", "25"))
                .andExpect(status().isOk());

        ArgumentCaptor<GetNearbyAlertsUseCase.NearbyAlertsQuery> captor =
            ArgumentCaptor.forClass(GetNearbyAlertsUseCase.NearbyAlertsQuery.class);
        verify(getNearbyAlertsUseCase).getNearbyAlerts(captor.capture());

        assertEquals(AlertType.FIRE, captor.getValue().type());
        assertEquals(AlertSeverity.CRITICAL, captor.getValue().severity());
        assertEquals(25, captor.getValue().limit());
    }

    /**
     * The ceiling is the whole protection: without it a caller sets limit=1000000 and the
     * cap is decorative.
     */
    @Test
    void shouldRejectALimitAboveTheCeiling() throws Exception {
        mockMvc.perform(get("/api/v1/alerts/nearby")
                        .param("latitude", "42.0")
                        .param("longitude", "23.0")
                        .param("limit", "501"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRejectAnUnknownAlertType() throws Exception {
        mockMvc.perform(get("/api/v1/alerts/nearby")
                        .param("latitude", "42.0")
                        .param("longitude", "23.0")
                        .param("type", "NOT_A_TYPE"))
                .andExpect(status().isBadRequest());
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
                .thenThrow(new org.example.nabat.incident.domain.AlertNotFoundException(
                        org.example.nabat.incident.domain.AlertId.generate()));

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
                Role.USER, true, false, now, now, 5, null, null, null,
        0);
        var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                mockUser, null, java.util.List.of());
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

        mockMvc.perform(patch("/api/v1/alerts/{id}/resolve", resolved.id().value()))
                .andExpect(status().isOk());
    }
}
