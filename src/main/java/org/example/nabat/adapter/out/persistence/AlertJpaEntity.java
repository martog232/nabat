package org.example.nabat.adapter.out.persistence;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.example.nabat.domain.model.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Getter
@Setter
@Table(name = "alerts")
public class AlertJpaEntity {

    // Getters за query projections
    @Getter
    @Id
    private UUID id;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertSeverity severity;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AlertStatus status;

    @Column(nullable = false)
    private UUID reportedBy;

    // JPA requires default constructor
    protected AlertJpaEntity() {
    }

    public static AlertJpaEntity from(Alert alert) {
        AlertJpaEntity entity = new AlertJpaEntity();
        entity.id = alert.id().value();
        entity.title = alert.title();
        entity.description = alert.description();
        entity.type = alert.type();
        entity.severity = alert.severity();
        entity.latitude = alert.location().latitude();
        entity.longitude = alert.location().longitude();
        entity.createdAt = alert.createdAt();
        entity.status = alert.status();
        entity.reportedBy = alert.reportedBy();
        return entity;
    }

    public Alert toDomain() {
        return new Alert(
                AlertId.of(id),
                title,
                description,
                type,
                severity,
                Location.of(latitude, longitude),
                createdAt,
                status,
                reportedBy,
                0,
                0,
                0
        );
    }
}
