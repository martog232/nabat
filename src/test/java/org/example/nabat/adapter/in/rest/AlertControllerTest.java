package org.example.nabat.adapter.in.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.nabat.adapter.in.security.JwtTokenProvider;
import org.example.nabat.application.port.in.CreateAlertUseCase;
import org.example.nabat.application.port.in.GetNearbyAlertsUseCase;
import org.example.nabat.application.port.out.UserRepository;
import org.example.nabat.domain.model.Alert;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.AlertSeverity;
import org.example.nabat.domain.model.AlertStatus;
import org.example.nabat.domain.model.AlertType;
import org.example.nabat.domain.model.Location;
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
                0, 0, 0
        );
    }

    @Test
    void shouldCreateAlertAndReturn201() throws Exception {
        Alert alert = buildAlert();
        when(createAlertUseCase.createAlert(any())).thenReturn(alert);

        CreateAlertRequest request = new CreateAlertRequest(
                "Test Alert",
                "Test description",
                AlertType.FIRE,
                AlertSeverity.HIGH,
                42.0,
                23.0,
                UUID.fromString("00000000-0000-0000-0000-000000000002")
        );

        mockMvc.perform(post("/api/v1/alerts")
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
                    "longitude": 23.0,
                    "reportedBy": "00000000-0000-0000-0000-000000000002"
                }
                """;

        mockMvc.perform(post("/api/v1/alerts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidRequest))
                .andExpect(status().isBadRequest());
    }
}
