package org.example.nabat.adapter.out.persistence;

import org.example.nabat.domain.model.VoteType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AlertVoteJpaRepository extends JpaRepository<AlertVoteJpaEntity, Long> {
    Optional<AlertVoteJpaEntity> findByAlertIdAndUserId(UUID alertId, UUID userId);

    void deleteByAlertIdAndUserId(UUID alertId, UUID userId);

    boolean existsByAlertIdAndUserId(UUID alertId, UUID userId);

    int countByAlertIdAndVoteType(UUID alertId, VoteType voteType);
}
