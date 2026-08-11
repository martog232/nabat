package org.example.nabat.incident.adapter.in.rest;

import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.example.nabat.incident.application.port.in.CreateAlertUseCase;
import org.example.nabat.incident.application.port.in.GetAlertByIdUseCase;
import org.example.nabat.incident.application.port.in.GetNearbyAlertsUseCase;
import org.example.nabat.incident.application.port.in.ListAlertsUseCase;
import org.example.nabat.incident.application.port.in.ResolveAlertUseCase;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.AlertSeverity;
import org.example.nabat.incident.domain.AlertStatus;
import org.example.nabat.incident.domain.AlertType;
import org.example.nabat.identity.domain.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts")
@Validated
public class AlertController {

    private final CreateAlertUseCase createAlertUseCase;
    private final GetNearbyAlertsUseCase getNearbyAlertsUseCase;
    private final GetAlertByIdUseCase getAlertByIdUseCase;
    private final ResolveAlertUseCase resolveAlertUseCase;
    private final ListAlertsUseCase listAlertsUseCase;

    public AlertController(
        CreateAlertUseCase createAlertUseCase,
        GetNearbyAlertsUseCase getNearbyAlertsUseCase,
        GetAlertByIdUseCase getAlertByIdUseCase,
        ResolveAlertUseCase resolveAlertUseCase,
        // An in-port, not the AlertRepository out-port this controller used to inject
        // directly — which let the REST layer reach past the application layer entirely.
        ListAlertsUseCase listAlertsUseCase
    ) {
        this.createAlertUseCase = createAlertUseCase;
        this.getNearbyAlertsUseCase = getNearbyAlertsUseCase;
        this.getAlertByIdUseCase = getAlertByIdUseCase;
        this.resolveAlertUseCase = resolveAlertUseCase;
        this.listAlertsUseCase = listAlertsUseCase;
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

    /**
     * Active alerts near a point.
     *
     * <p>All three parameters are now bounded. They were previously unvalidated, so
     * {@code radiusKm=100000} returned the entire table and any coordinate value was
     * accepted.
     *
     * @param since optional ISO-8601 instant; when present only alerts created at or
     *              after it are returned. Used by clients catching up after a dropped
     *              WebSocket connection — the frontend was already sending this
     *              parameter, and it was being silently ignored.
     */
    @GetMapping("/nearby")
    @Operation(summary = "Active alerts within a radius, optionally filtered and newer than `since`")
    public ResponseEntity<NearbyAlertsResponse> getNearbyAlerts(
        @RequestParam @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @RequestParam @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @RequestParam(defaultValue = "5.0")
        @DecimalMin(value = "0.0", inclusive = false) @DecimalMax("100.0") Double radiusKm,
        @RequestParam(required = false) Instant since,
        @RequestParam(required = false) AlertType type,
        @RequestParam(required = false) AlertSeverity severity,
        @RequestParam(defaultValue = "100")
        @Min(1) @Max(GetNearbyAlertsUseCase.NearbyAlertsQuery.MAX_LIMIT) Integer limit
    ) {
        var query = new GetNearbyAlertsUseCase.NearbyAlertsQuery(
            latitude, longitude, radiusKm, type, severity, limit);

        List<Alert> alerts = since == null
            ? getNearbyAlertsUseCase.getNearbyAlerts(query)
            : getNearbyAlertsUseCase.getAlertsSince(query, since);

        return ResponseEntity.ok(NearbyAlertsResponse.of(
            alerts.stream().map(AlertResponse::from).toList(),
            limit
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AlertResponse> getById(@PathVariable UUID id) {
        Alert alert = getAlertByIdUseCase.getById(AlertId.of(id));
        return ResponseEntity.ok(AlertResponse.from(alert));
    }

    /**
     * Marks an alert resolved.
     *
     * <p>{@code PATCH} only. There used to be a {@code POST} mapping on the same path
     * with a byte-identical body; PATCH is the correct verb for a partial state change
     * and is what the frontend calls.
     */
    @PatchMapping("/{id}/resolve")
    public ResponseEntity<AlertResponse> resolve(
            @PathVariable UUID id,
            @AuthenticationPrincipal User currentUser
    ) {
        Alert resolved = resolveAlertUseCase.resolve(AlertId.of(id), currentUser);
        return ResponseEntity.ok(AlertResponse.from(resolved));
    }

    /** Admin-only listing of all alerts by status (defaults to ACTIVE). */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<AlertResponse>> listByStatus(
            @RequestParam(defaultValue = "ACTIVE") AlertStatus status
    ) {
        List<AlertResponse> response = listAlertsUseCase.listByStatus(status).stream()
                .map(AlertResponse::from)
                .toList();
        return ResponseEntity.ok(response);
    }
}
