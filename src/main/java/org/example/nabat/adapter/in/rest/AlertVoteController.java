package org.example.nabat.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.example.nabat.application.port.in.VoteAlertUseCase;
import org.example.nabat.application.port.in.VoteAlertUseCase.VoteCommand;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.VoteType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts/{alertId}/votes")
public class AlertVoteController {

    private final VoteAlertUseCase voteAlertUseCase;

    public AlertVoteController(VoteAlertUseCase voteAlertUseCase) {
        this.voteAlertUseCase = voteAlertUseCase;
    }

    /**
     * Casts or changes the caller's vote.
     *
     * <p>The response carries the resulting tallies so the client does not need a
     * follow-up call to {@code /stats} — which, being served from an eventually
     * consistent projection, would in any case have returned the pre-vote counts.
     */
    @PostMapping
    public ResponseEntity<VoteResponse> vote(
            @PathVariable UUID alertId,
            @Valid @RequestBody VoteRequest request,
            @AuthenticationPrincipal User currentUser
    ) {
        VoteCommand command = new VoteCommand(
                AlertId.of(alertId),
                currentUser.id(),
                request.voteType()
        );

        VoteAlertUseCase.VoteReceipt vote = voteAlertUseCase.vote(command);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(VoteResponse.from(vote));
    }

    /** Removes the caller's vote and returns the resulting tallies. */
    @DeleteMapping
    public ResponseEntity<VoteStatsResponse> removeVote(
            @PathVariable UUID alertId,
            @AuthenticationPrincipal User currentUser
    ) {
        VoteAlertUseCase.VoteStats stats = voteAlertUseCase.removeVote(
                AlertId.of(alertId),
                currentUser.id()
        );

        return ResponseEntity.ok(VoteStatsResponse.from(stats));
    }

    @GetMapping("/stats")
    public ResponseEntity<VoteStatsResponse> getStats(@PathVariable UUID alertId) {
        VoteAlertUseCase.VoteStats stats = voteAlertUseCase.getVoteStats(AlertId.of(alertId));
        return ResponseEntity.ok(VoteStatsResponse.from(stats));
    }

    @GetMapping("/me")
    public ResponseEntity<UserVoteResponse> getMyVote(
            @PathVariable UUID alertId,
            @AuthenticationPrincipal User currentUser
    ) {
        Optional<VoteType> myVote = voteAlertUseCase.findUserVote(
                AlertId.of(alertId),
                currentUser.id()
        );

        return ResponseEntity.ok(new UserVoteResponse(myVote.isPresent(), myVote.orElse(null)));
    }

    @Schema(description = "Request body for casting a vote on an alert")
    public record VoteRequest(
            @Schema(description = "Type of vote to cast", example = "UPVOTE")
            @NotNull(message = "voteType is required")
            VoteType voteType) {
    }

    @Schema(description = "The recorded vote and the resulting tallies")
    public record VoteResponse(
            @Schema(description = "Vote identifier") UUID id,
            @Schema(description = "ID of the alert that was voted on") UUID alertId,
            @Schema(description = "Type of vote") VoteType voteType,
            @Schema(description = "When the vote was recorded") Instant createdAt,
            @Schema(description = "Tallies after this vote") VoteStatsResponse stats) {

        public static VoteResponse from(VoteAlertUseCase.VoteReceipt vote) {
            return new VoteResponse(
                    vote.id(),
                    vote.alertId().value(),
                    vote.voteType(),
                    vote.createdAt(),
                    VoteStatsResponse.from(vote.stats())
            );
        }
    }

    @Schema(description = "Aggregate vote statistics for an alert")
    public record VoteStatsResponse(
            @Schema(description = "Number of upvotes") int upvotes,
            @Schema(description = "Number of downvotes") int downvotes,
            @Schema(description = "Number of confirmations") int confirmations,
            @Schema(description = "Credibility score as computed by the voting service") int credibilityScore
    ) {
        public static VoteStatsResponse from(VoteAlertUseCase.VoteStats stats) {
            return new VoteStatsResponse(
                    stats.upvotes(),
                    stats.downvotes(),
                    stats.confirmations(),
                    stats.credibilityScore()
            );
        }
    }

    @Schema(description = "Indicates whether the current user has voted on an alert")
    public record UserVoteResponse(
            @Schema(description = "true if the authenticated user has an active vote") boolean hasVoted,
            @Schema(description = "The vote type, or null if hasVoted is false") VoteType voteType
    ) {}
}
