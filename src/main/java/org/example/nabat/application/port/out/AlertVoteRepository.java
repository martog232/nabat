package org.example.nabat.application.port.out;

import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.AlertVote;
import org.example.nabat.domain.model.UserId;
import org.example.nabat.domain.model.VoteType;

import java.util.Optional;

public interface AlertVoteRepository {

    AlertVote save(AlertVote vote);

    Optional<AlertVote> findByAlertIdAndUserId(AlertId alertId, UserId userId);

    void deleteByAlertIdAndUserId(AlertId alertId, UserId userId);

    boolean existsByAlertIdAndUserId(AlertId alertId, UserId userId);

    int countByAlertIdAndVoteType(AlertId alertId, VoteType voteType);
}