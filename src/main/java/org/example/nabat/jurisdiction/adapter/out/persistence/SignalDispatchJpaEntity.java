package org.example.nabat.jurisdiction.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.Getter;
import org.example.nabat.jurisdiction.domain.SignalDispatch;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "signal_dispatches")
@Getter
public class SignalDispatchJpaEntity implements Persistable<UUID> {

    @Id
    @Column(name = "alert_id")
    private UUID alertId;

    @Column(name = "district_code", nullable = false, length = 32)
    private String districtCode;

    @Column(nullable = false, length = 254)
    private String recipient;

    @Column(name = "sent_at", nullable = false)
    private Instant sentAt;

    /**
     * Every instance built here is a new row. Without this, an assigned id makes Spring Data
     * treat the entity as existing and {@code save} merges it — overwriting a dispatch that
     * is already recorded instead of failing on the primary key.
     */
    @Override
    @Transient
    public boolean isNew() {
        return true;
    }

    @Override
    public UUID getId() {
        return alertId;
    }

    protected SignalDispatchJpaEntity() {
    }

    static SignalDispatchJpaEntity from(SignalDispatch dispatch) {
        SignalDispatchJpaEntity entity = new SignalDispatchJpaEntity();
        entity.alertId = dispatch.alertId();
        entity.districtCode = dispatch.districtCode();
        entity.recipient = dispatch.recipient();
        entity.sentAt = dispatch.sentAt();
        return entity;
    }
}
