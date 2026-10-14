package org.example.nabat.jurisdiction.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.example.nabat.jurisdiction.domain.District;

/**
 * A row of {@code districts}, without its boundary.
 *
 * <p>The geometry column is deliberately unmapped. Mapping it would bring in
 * hibernate-spatial and a JTS type for a value Java never reads; containment is asked
 * in SQL, in {@link DistrictJpaRepository}. {@code ddl-auto=validate} only checks the
 * columns that are mapped, so the unmapped one is not a validation failure.
 *
 * <p>Read-only by construction: districts are reference data, written by migrations.
 */
@Entity
@Table(name = "districts")
@Getter
public class DistrictJpaEntity {

    @Id
    @Column(length = 32)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "osm_relation_id", nullable = false)
    private long osmRelationId;

    @Column(name = "signal_email", nullable = false, length = 254)
    private String signalEmail;

    /** An estimate, for ratios — see V25. */
    @Column(nullable = false)
    private int population;

    protected DistrictJpaEntity() {
    }

    public District toDomain() {
        return new District(code, name, signalEmail, population);
    }
}
