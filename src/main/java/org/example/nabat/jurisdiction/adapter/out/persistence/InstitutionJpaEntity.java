package org.example.nabat.jurisdiction.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import org.example.nabat.jurisdiction.domain.Institution;

/** A row of {@code institutions} (V29). Reference data, written by migrations only. */
@Entity
@Table(name = "institutions")
@Getter
public class InstitutionJpaEntity {

    @Id
    @Column(length = 32)
    private String code;

    @Column(nullable = false, length = 200)
    private String name;

    @Column(name = "signal_email", nullable = false, length = 254)
    private String signalEmail;

    protected InstitutionJpaEntity() {
    }

    public Institution toDomain() {
        return new Institution(code, name, signalEmail);
    }
}
