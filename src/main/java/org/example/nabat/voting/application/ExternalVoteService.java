package org.example.nabat.voting.application;

import org.example.nabat.notification.domain.NotificationType;
import org.example.nabat.notification.domain.NotificationMilestones;
import lombok.RequiredArgsConstructor;
import org.example.nabat.shared.UseCase;
import org.example.nabat.notification.application.port.in.SendNotificationUseCase;
import org.example.nabat.voting.application.port.in.VoteAlertUseCase;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.voting.application.port.out.ExternalVotingPort;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.voting.domain.VoteType;

import java.util.Optional;

/**
 * Casts votes against the nabat-voting service.
 *
 * <h2>What this no longer does</h2>
 * It used to write the vote counts onto the local alert and broadcast the update, from the
 * tallies that came back with the vote. Both now happen in {@link VoteTalliesProjectionService},
 * driven by {@code vote.cast} and {@code vote.removed}. The reason is durability: the local
 * write rode on this request, so a crash between the remote vote and it left the counts wrong
 * until somebody voted on that alert again. An event is retried until it is applied.
 *
 * <p>The vote itself stays synchronous — the caller has to be told whether it was accepted,
 * and with what result — and the tallies still travel back in the response, so a client sees
 * its own vote immediately. What became eventually consistent is the copy of the counts on
 * the alert, by a Kafka round trip.
 *
 * <h2>Why the notification did not move with them</h2>
 * At-least-once delivery would make a redelivered event a second notification to the alert's
 * owner. A projection write does not care — it is absolute — but a notification is not
 * idempotent without a dedupe key, and that belongs with the notification service in phase 6,
 * where the event catalogue already lists it as a consumer of these topics.
 *
 * <h2>Why there is no {@code @Transactional} on these methods</h2>
 * Each of them starts with a network call to the voting service. Wrapping the whole flow in a
 * transaction — which is what this class used to do — meant holding a pooled database
 * connection open across two or three HTTP round-trips, and it bought no atomicity anyway:
 * the remote vote commits independently, so a local rollback afterwards just left the two
 * stores permanently disagreeing.
 */
@UseCase
@RequiredArgsConstructor
public class ExternalVoteService implements VoteAlertUseCase {

    private final ExternalVotingPort externalVotingPort;
    private final AlertRepository alertRepository;
    private final SendNotificationUseCase sendNotificationUseCase;

    @Override
    public VoteReceipt vote(VoteCommand command) {
        ExternalVotingPort.VoteResult result = externalVotingPort.vote(
                command.alertId(),
                command.userId(),
                command.voteType()
        );

        VoteStats stats = toStats(result.stats());

        // Read, not written: the counts on this row are the consumer's business now. The
        // alert is fetched for the owner's id and title, and it can legitimately be absent —
        // the voting service keeps votes by alert id and is never told about deletions.
        alertRepository.findById(command.alertId())
                .ifPresent(alert -> notifyAlertOwner(alert, command, stats.confirmations()));

        return new VoteReceipt(
                result.id(),
                result.alertId(),
                result.voteType(),
                result.createdAt(),
                stats
        );
    }

    @Override
    public VoteStats removeVote(AlertId alertId, UserId userId) {
        return toStats(externalVotingPort.removeVote(alertId, userId));
    }

    /**
     * No transaction: this is a single remote read with no local database access, so
     * the {@code @Transactional(readOnly = true)} that used to be here only checked
     * a connection out of the pool for the duration of an HTTP call.
     */
    @Override
    public Optional<VoteType> findUserVote(AlertId alertId, UserId userId) {
        return externalVotingPort.findUserVote(alertId, userId);
    }

    @Override
    public VoteStats getVoteStats(AlertId alertId) {
        return toStats(externalVotingPort.getVoteStats(alertId));
    }

    private void notifyAlertOwner(Alert alert, VoteCommand command, int confirmations) {
        // Voting on your own alert should not notify you.
        if (alert.reportedBy().equals(command.userId().value())) {
            return;
        }

        UserId ownerId = UserId.of(alert.reportedBy());
        sendNotificationUseCase.sendVoteNotification(new SendNotificationUseCase.VoteNotificationCommand(
                ownerId,
                command.userId(),
                command.alertId(),
                alert.title(),
                notificationTypeFor(command.voteType())
        ));

        if (command.voteType() == VoteType.CONFIRM && NotificationMilestones.isMilestone(confirmations)) {
            sendNotificationUseCase.sendMilestoneNotification(
                    new SendNotificationUseCase.MilestoneNotificationCommand(
                            ownerId,
                            command.alertId(),
                            alert.title(),
                            confirmations
                    ));
        }
    }

    /**
     * Translates this module's vote vocabulary into the notification module's.
     *
     * <p>The mapping belongs on this side of the call: the notification module must not
     * have to know what a {@link VoteType} is, or it would depend on the module that is
     * already calling it.
     */
    private static NotificationType notificationTypeFor(VoteType voteType) {
        return switch (voteType) {
            case UPVOTE -> NotificationType.ALERT_UPVOTED;
            case DOWNVOTE -> NotificationType.ALERT_DOWNVOTED;
            case CONFIRM -> NotificationType.ALERT_CONFIRMED;
        };
    }

    private static VoteStats toStats(ExternalVotingPort.VoteStats stats) {
        return new VoteStats(
                stats.upvotes(),
                stats.downvotes(),
                stats.confirmations(),
                stats.credibilityScore()
        );
    }
}
