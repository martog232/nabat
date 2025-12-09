package org.example.nabat.adapter.in.rest;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.nabat.application.port.in.CreateAlertUseCase;
import org.example.nabat.domain.model.AlertSeverity;
import org.example.nabat.domain.model.AlertType;

import java.util.UUID;

public record CreateAlertRequest(
    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be less than 200 characters")
    String title,

    @NotBlank(message = "Description is required")
    @Size(max = 2000, message = "Description must be less than 2000 characters")
    String description,

    @NotNull(message = "Alert type is required")
    AlertType type,

    @NotNull(message = "Severity is required")
    AlertSeverity severity,

    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0")
    Double latitude,

    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0")
    Double longitude,

    @NotNull(message = "Reporter ID is required")
    UUID reportedBy
) {
    public CreateAlertUseCase.CreateAlertCommand toCommand() {
        return new CreateAlertUseCase.CreateAlertCommand(
            title, description, type, severity, latitude, longitude, reportedBy
        );
    }
}
