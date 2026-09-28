package org.example.nabat.jurisdiction.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface SignalDispatchJpaRepository extends JpaRepository<SignalDispatchJpaEntity, UUID> {
}
