package org.example.nabat.voting.application;

import lombok.RequiredArgsConstructor;
import org.example.nabat.incident.application.port.out.AlertNotificationPort;
import org.example.nabat.incident.application.port.out.AlertRepository;
import org.example.nabat.shared.UseCase;
import org.example.nabat.voting.application.port.in.ApplyVoteTalliesUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Keeps the denormalised vote counts on {@code alerts} in step with the voting service.
 *
 * <p>This used to happen on the request thread, from the tallies that came back with the
 * vote. That write was lost whenever this service died between the remote vote and the local
 * write, and the projection then stayed wrong until somebody voted on that alert again —
 * which the code admitted to and called self-healing. Driving it from {@code vote.cast}
 * instead makes the update durable: the event is retried until it is applied.
 *
 * <p>The caller of a vote still gets fresh numbers, because those come back in the vote's own
 * response. What is eventually consistent now is the copy on the alert — the one the list and
 * detail views read — and the window is a Kafka round trip.
 *
 * <p>No {@code @Transactional}: {@link AlertRepository#applyVoteCounts} is a single write and
 * carries its own. The broadcast deliberately happens after it, and outside it, so a socket
 * that is slow to accept cannot hold a database transaction open.
 */
@UseCase
@RequiredArgsConstructor
public class VoteTalliesProjectionService implements ApplyVoteTalliesUseCase {

    private static final Logger log = LoggerFactory.getLogger(VoteTalliesProjectionService.class);

    private final AlertRepository alertRepository;
    private final AlertNotificationPort alertNotificationPort;

    @Override
    public void applyTallies(VoteTalliesUpdate update) {
        alertRepository.applyVoteCounts(
                update.alertId(),
                update.upvotes(),
                update.downvotes(),
                update.confirmations(),
                update.credibilityScore()
        ).ifPresentOrElse(
                alertNotificationPort::broadcastAlertUpdate,
                // Not an error: the alert can legitimately have been deleted here while
                // still existing in the voting service, which keeps votes by alert id and
                // is never told about deletions.
                () -> log.debug("Vote tallies for unknown alert {}; nothing to update", update.alertId())
        );
    }
}
