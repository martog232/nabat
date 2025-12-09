package org.example.nabat.adapter.in.rest;

import jakarta.validation.Valid;
import org.example.nabat.application.port.in.CreateAlertUseCase;
import org.example.nabat.application.port.in.GetNearbyAlertsUseCase;
import org.example.nabat.domain.model.Alert;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final CreateAlertUseCase createAlertUseCase;
    private final GetNearbyAlertsUseCase getNearbyAlertsUseCase;

    public AlertController(
        CreateAlertUseCase createAlertUseCase,
        GetNearbyAlertsUseCase getNearbyAlertsUseCase
    ) {
        this.createAlertUseCase = createAlertUseCase;
        this.getNearbyAlertsUseCase = getNearbyAlertsUseCase;
    }

    @PostMapping
    public ResponseEntity<AlertResponse> createAlert(@Valid @RequestBody CreateAlertRequest request) {
        var command = request.toCommand();
        Alert alert = createAlertUseCase.createAlert(command);
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(AlertResponse.from(alert));
    }

    @GetMapping("/nearby")
    public ResponseEntity<List<AlertResponse>> getNearbyAlerts(
        @RequestParam double latitude,
        @RequestParam double longitude,
        @RequestParam(defaultValue = "5.0") double radiusKm
    ) {
        var query = new GetNearbyAlertsUseCase.NearbyAlertsQuery(latitude, longitude, radiusKm);
        List<Alert> alerts = getNearbyAlertsUseCase.getNearbyAlerts(query);

        List<AlertResponse> response = alerts.stream()
                                             .map(AlertResponse::from)
                                             .toList();

        return ResponseEntity.ok(response);
    }
}
