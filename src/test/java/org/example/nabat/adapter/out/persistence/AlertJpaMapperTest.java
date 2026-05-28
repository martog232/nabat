package org.example.nabat.adapter.out.persistence;

import org.example.nabat.domain.model.Alert;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.AlertSeverity;
import org.example.nabat.domain.model.AlertStatus;
import org.example.nabat.domain.model.AlertType;
import org.example.nabat.domain.model.Location;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AlertJpaMapperTest {

    private final AlertJpaMapper mapper = new AlertJpaMapper() {
    };

    @Test
    void toEntityMapsAlertIdToUuid() {
        UUID id = UUID.randomUUID();
        UUID reportedBy = UUID.randomUUID();
        Alert alert = new Alert(
            AlertId.of(id),
            "Road incident",
            "Lane blocked",
            AlertType.ACCIDENT,
            AlertSeverity.HIGH,
            Location.of(42.6977, 23.3219),
            Instant.now(),
            AlertStatus.ACTIVE,
            reportedBy,
            2,
            1,
            0,
            null
        );

        AlertJpaEntity entity = mapper.toEntity(alert);

        assertNotNull(entity);
        assertEquals(id, entity.getId());
        assertEquals(alert.title(), entity.getTitle());
        assertEquals(alert.location().latitude(), entity.getLatitude());
        assertEquals(alert.location().longitude(), entity.getLongitude());
    }

    @Test
    void toDomainMapsUuidToAlertId() {
        UUID id = UUID.randomUUID();
        UUID reportedBy = UUID.randomUUID();
        AlertJpaEntity entity = new AlertJpaEntity();
        entity.setId(id);
        entity.setTitle("Road incident");
        entity.setDescription("Lane blocked");
        entity.setType(AlertType.ACCIDENT);
        entity.setSeverity(AlertSeverity.HIGH);
        entity.setLatitude(42.6977);
        entity.setLongitude(23.3219);
        entity.setCreatedAt(Instant.now());
        entity.setStatus(AlertStatus.ACTIVE);
        entity.setReportedBy(reportedBy);
        entity.setUpvoteCount(2);
        entity.setDownvoteCount(1);
        entity.setConfirmationCount(0);
        entity.setResolvedAt(null);

        Alert alert = mapper.toDomain(entity);

        assertNotNull(alert);
        assertEquals(id, alert.id().value());
        assertEquals(entity.getTitle(), alert.title());
        assertEquals(entity.getLatitude(), alert.location().latitude());
        assertEquals(entity.getLongitude(), alert.location().longitude());
    }
}

