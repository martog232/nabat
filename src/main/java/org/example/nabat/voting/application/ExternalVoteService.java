package org.example.nabat.voting.application;

import org.example.nabat.notification.domain.NotificationType;
import org.example.nabat.notification.domain.NotificationMilestones;
import lombok.RequiredArgsConstructor;
import org.example.nabat.shared.UseCase;
import org.example.nabat.notification.application.port.in.SendNotificationUseCase;
import org.example.nabat.voting.application.port.in.VoteAlertUseCase;
import org.example.nabat.incident.application.port.out.AlertNotificationPort;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.voting.application.port.out.ExternalVotingPort;
import org.example.nabat.incident.domain.Alert;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.voting.domain.VoteType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;

/**
 * Casts votes against the nabat-voting service and keeps this service's
 * denormalised projection and real-time pushes in step.
 *
 * <h2>Why there is no {@code @Transactional} on these methods</h2>
 * Each of them starts with a network call to the voting service. Wrapping the whole
 * flow in a transaction — which is what this class used to do — meant holding a
 * pooled database connection open across two or three HTTP round-trips, and it
 * bought no atomicity anyway: the remote vote commits independently, so a local
 * rollback afterwards just left the two stores permanently disagreeing.
 *
 * <p>The local write is instead a single short transaction inside
 * {@link AlertRepository#applyVoteCounts}, and the projection is self-healing —
 * any subsequent vote re-syncs it from the voting service's authoritative counts.
 */
@UseCase
@RequiredArgsConstructor
public class ExternalVoteService implements VoteAlertUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExternalVoteService.class);

    private final ExternalVotingPort externalVotingPort;
    private final AlertRepository alertRepository;
    private final SendNotificationUseCase sendNotificationUseCase;
    private final AlertNotificationPort alertNotificationPort;

    @Override
    public VoteReceipt vote(VoteCommand command) {
        ExternalVotingPort.VoteResult result = externalVotingPort.vote(
                command.alertId(),
                command.userId(),
                command.voteType()
        );

        VoteStats stats = toStats(result.stats());

        // One read+write instead of update + two separate findById calls.
        Optional<Alert> updated = syncProjection(command.alertId(), stats);

        updated.ifPresent(alert -> {
            notifyAlertOwner(alert, command, stats.confirmations());
            alertNotificationPort.broadcastAlertUpdate(alert);
        });

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
        VoteStats stats = toStats(externalVotingPort.removeVote(alertId, userId));

        syncProjection(alertId, stats)
                .ifPresent(alertNotificationPort::broadcastAlertUpdate);

        return stats;
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

    /**
     * Mirrors the voting service's tallies into the {@code alerts} row.
     *
     * <p>Returns empty when the alert is unknown locally — possible if it was
     * deleted between the vote and this write — in which case there is nothing to
     * notify or broadcast about.
     */
    private Optional<Alert> syncProjection(AlertId alertId, VoteStats stats) {
        Optional<Alert> updated = alertRepository.applyVoteCounts(
                alertId,
                stats.upvotes(),
                stats.downvotes(),
                stats.confirmations(),
                stats.credibilityScore()
        );

        if (updated.isEmpty()) {
            log.warn("Voted on alert {} but it no longer exists locally; projection not updated", alertId);
        }
        return updated;
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
