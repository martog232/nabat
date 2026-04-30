package org.example.nabat.adapter.in.rest;

import jakarta.validation.Valid;
import org.example.nabat.application.port.in.CreateAlertUseCase;
import org.example.nabat.application.port.in.GetAlertByIdUseCase;
import org.example.nabat.application.port.in.GetNearbyAlertsUseCase;
import org.example.nabat.application.port.in.ResolveAlertUseCase;
import org.example.nabat.application.port.out.AlertRepository;
import org.example.nabat.domain.model.Alert;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.AlertStatus;
import org.example.nabat.domain.model.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
public class AlertController {

    private final CreateAlertUseCase createAlertUseCase;
    private final GetNearbyAlertsUseCase getNearbyAlertsUseCase;
    private final GetAlertByIdUseCase getAlertByIdUseCase;
    private final ResolveAlertUseCase resolveAlertUseCase;
    private final AlertRepository alertRepository;

    public AlertController(
        CreateAlertUseCase createAlertUseCase,
        GetNearbyAlertsUseCase getNearbyAlertsUseCase,
        GetAlertByIdUseCase getAlertByIdUseCase,
        ResolveAlertUseCase resolveAlertUseCase,
        AlertRepository alertRepository
    ) {
        this.createAlertUseCase = createAlertUseCase;
        this.getNearbyAlertsUseCase = getNearbyAlertsUseCase;
        this.getAlertByIdUseCase = getAlertByIdUseCase;
        this.resolveAlertUseCase = resolveAlertUseCase;
        this.alertRepository = alertRepository;
    }

    @PostMapping
    public ResponseEntity<AlertResponse> createAlert(
            @Valid @RequestBody CreateAlertRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        var command = request.toCommand(currentUser.id().value());
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

    @GetMapping("/{id}")
    public ResponseEntity<AlertResponse> getById(@PathVariable UUID id) {
        Alert alert = getAlertByIdUseCase.getById(AlertId.of(id));
        return ResponseEntity.ok(AlertResponse.from(alert));
    }

    @PostMapping("/{id}/resolve")
    public ResponseEntity<AlertResponse> resolve(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser
    ) {
        Alert resolved = resolveAlertUseCase.resolve(AlertId.of(id), currentUser);
        return ResponseEntity.ok(AlertResponse.from(resolved));
    }

    /** Admin-only listing of all alerts by status (defaults to ACTIVE). Demonstrates T-15. */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AlertResponse>> listByStatus(
            @RequestParam(defaultValue = "ACTIVE") AlertStatus status
    ) {
        List<AlertResponse> response = alertRepository.findByStatus(status).stream()
                .map(AlertResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }
}
