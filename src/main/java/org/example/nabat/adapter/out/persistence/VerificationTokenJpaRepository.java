package org.example.nabat.adapter.out.persistence;

import org.example.nabat.domain.model.VerificationTokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface VerificationTokenJpaRepository extends JpaRepository<VerificationTokenJpaEntity, String> {

    Optional<VerificationTokenJpaEntity> findByIdAndType(String id, VerificationTokenType type);

    @Modifying
    @Query("DELETE FROM VerificationTokenJpaEntity t WHERE t.userId = :userId AND t.type = :type")
    void deleteByUserIdAndType(@Param("userId") UUID userId, @Param("type") VerificationTokenType type);
}

