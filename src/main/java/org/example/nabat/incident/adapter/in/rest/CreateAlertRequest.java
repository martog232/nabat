package org.example.nabat.incident.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.nabat.incident.application.port.in.CreateAlertUseCase;
import org.example.nabat.incident.domain.AlertType;

import java.util.UUID;

@Schema(description = "Request body for reporting a problem in the city")
public record CreateAlertRequest(
    @Schema(description = "Short title of the alert", example = "Дупка на ул. Шипка", maxLength = 200)
    @NotBlank(message = "Title is required")
    @Size(max = 200, message = "Title must be less than 200 characters")
    String title,

    @Schema(description = "Detailed description of the alert", example = "Дълбока дупка в дясното платно, пред номер 12", maxLength = 2000)
    @NotBlank(message = "Description is required")
    @Size(max = 2000, message = "Description must be less than 2000 characters")
    String description,

    @Schema(description = "Category of the problem", example = "POTHOLE")
    @NotNull(message = "Alert type is required")
    AlertType type,

    @Schema(description = "WGS-84 latitude of the problem", example = "42.6977", minimum = "-90", maximum = "90")
    @NotNull(message = "Latitude is required")
    @DecimalMin(value = "-90.0") @DecimalMax(value = "90.0")
    Double latitude,

    @Schema(description = "WGS-84 longitude of the problem", example = "23.3219", minimum = "-180", maximum = "180")
    @NotNull(message = "Longitude is required")
    @DecimalMin(value = "-180.0") @DecimalMax(value = "180.0")
    Double longitude,

    @Schema(description = "URL of an uploaded photo for this alert", example = "/api/v1/uploads/uuid.jpg")
    String photoUrl
) {
    public CreateAlertUseCase.CreateAlertCommand toCommand(UUID reportedBy) {
        return new CreateAlertUseCase.CreateAlertCommand(
            title, description, type, latitude, longitude, reportedBy, photoUrl
        );
    }
}
