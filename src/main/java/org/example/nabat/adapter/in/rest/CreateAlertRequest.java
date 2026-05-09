package org.example.nabat.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.nabat.application.port.in.CreateAlertUseCase;
import org.example.nabat.domain.model.AlertSeverity;
import org.example.nabat.domain.model.AlertType;

import java.util.UUID;

@Schema(description = "Request body for creating a new safety alert")
public record CreateAlertRequest(
    @Schema(description = "Short title of the alert", example = "Gas leak on Main St", maxLength = 200)
    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be less than 200 characters")
    String title,

    @Schema(description = "Detailed description of the alert", example = "Strong smell of gas near intersection", maxLength = 2000)
    @NotBlank(message = "Description is required")
    @Size(max = 2000, message = "Description must be less than 2000 characters")
    String description,

    @Schema(description = "Category of the alert", example = "GAS_LEAK")
    @NotNull(message = "Alert type is required")
    AlertType type,

    @Schema(description = "Severity level", example = "HIGH")
    @NotNull(message = "Severity is required")
    AlertSeverity severity,

    @Schema(description = "WGS-84 latitude of the incident", example = "40.7128", minimum = "-90", maximum = "90")
    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0")
    Double latitude,

    @Schema(description = "WGS-84 longitude of the incident", example = "-74.0060", minimum = "-180", maximum = "180")
    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0")
    Double longitude
) {
    public CreateAlertUseCase.CreateAlertCommand toCommand(UUID reportedBy) {
        return new CreateAlertUseCase.CreateAlertCommand(
            title, description, type, severity, latitude, longitude, reportedBy
        );
    }
}
