package org.example.nabat.adapter.out.persistence;

import lombok.RequiredArgsConstructor;
import org.example.nabat.application.port.out.AlertVoteRepository;
import org.example.nabat.domain.model.*;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AlertVoteRepositoryAdapter implements AlertVoteRepository {

    private final AlertVoteJpaRepository alertVoteJpaRepository;


    @Override
    public AlertVote save(AlertVote vote) {
        AlertVoteJpaEntity entity = toJpaEntity(vote);
        AlertVoteJpaEntity savedEntity = alertVoteJpaRepository.save(entity);
        return toDomain(savedEntity);
    }

    @Override
    public Optional<AlertVote> findByAlertIdAndUserId(AlertId alertId, UserId userId) {
        return alertVoteJpaRepository.findByAlertIdAndUserId(alertId.value(), userId.value()).map(this::toDomain);
    }

    @Override
    public void deleteByAlertIdAndUserId(AlertId alertId, UserId userId) {
        alertVoteJpaRepository.deleteByAlertIdAndUserId(alertId.value(), userId.value());
    }

    @Override
    public boolean existsByAlertIdAndUserId(AlertId alertId, UserId userId) {
        return alertVoteJpaRepository.existsByAlertIdAndUserId(alertId.value(), userId.value());
    }

    @Override
    public int countByAlertIdAndVoteType(AlertId alertId, VoteType voteType) {
        return alertVoteJpaRepository.countByAlertIdAndVoteType(alertId.value(), voteType);
    }

    private AlertVoteJpaEntity toJpaEntity(AlertVote vote) {
        return new AlertVoteJpaEntity(
                vote.id().value(),
                vote.alertId().value(),
                vote.userId().value(),
                vote.voteType(),
                vote.createdAt()
        );
    }

    private AlertVote toDomain(AlertVoteJpaEntity entity) {
        return new AlertVote(
                AlertVoteId.of(entity.getId()),
                AlertId.of(entity.getAlertId()),
                UserId.of(entity.getUserId()),
                entity.getVoteType(),
                entity.getCreatedAt()
        );
    }
}
