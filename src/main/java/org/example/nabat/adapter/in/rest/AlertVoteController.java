package org.example.nabat.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.VoteType;
import org.example.nabat.application.port.in.VoteAlertUseCase;
import org.example.nabat.application.port.in.VoteAlertUseCase.VoteCommand;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/alerts/{alertId}/votes")
public class AlertVoteController {

    private final VoteAlertUseCase voteAlertUseCase;

    public AlertVoteController(VoteAlertUseCase voteAlertUseCase) {
        this.voteAlertUseCase = voteAlertUseCase;
    }

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

    // DELETE /api/v1/alerts/{alertId}/votes — remove the current user's vote.
    @DeleteMapping
    public ResponseEntity<Void> removeVote(
            @PathVariable UUID alertId,
            @AuthenticationPrincipal User currentUser
    ) {
        voteAlertUseCase.removeVote(
                AlertId.of(alertId),
                currentUser.id()
        );

        return ResponseEntity.noContent().build();
    }

    // GET /api/v1/alerts/{alertId}/votes/stats — aggregate vote counts.
    @GetMapping("/stats")
    public ResponseEntity<VoteStatsResponse> getStats(@PathVariable UUID alertId) {
        VoteAlertUseCase.VoteStats stats = voteAlertUseCase.getVoteStats(AlertId.of(alertId));
        return ResponseEntity.ok(VoteStatsResponse.from(stats));
    }

    // GET /api/v1/alerts/{alertId}/votes/me — has the current user voted?
    @GetMapping("/me")
    public ResponseEntity<UserVoteResponse> getMyVote(
            @PathVariable UUID alertId,
            @AuthenticationPrincipal User currentUser
    ) {
        boolean hasVoted = voteAlertUseCase.hasUserVoted(
                AlertId.of(alertId),
                currentUser.id()
        );

        return ResponseEntity.ok(new UserVoteResponse(hasVoted));
    }

    @Schema(description = "Request body for casting a vote on an alert")
    public record VoteRequest(
            @Schema(description = "Type of vote to cast", example = "UPVOTE") @NotNull VoteType voteType) {
    }

    @Schema(description = "The recorded vote")
    public record VoteResponse(
            @Schema(description = "Vote identifier") UUID id,
            @Schema(description = "ID of the alert that was voted on") UUID alertId,
            @Schema(description = "Type of vote") VoteType voteType,
            @Schema(description = "ISO-8601 timestamp when the vote was cast") String createdAt) {
        public static VoteResponse from(VoteAlertUseCase.VoteReceipt vote) {
            return new VoteResponse(
                    vote.id(),
                    vote.alertId().value(),
                    vote.voteType(),
                    vote.createdAt().toString()
            );
        }
    }

    @Schema(description = "Aggregate vote statistics for an alert")
    public record VoteStatsResponse(
            @Schema(description = "Number of upvotes") int upvotes,
            @Schema(description = "Number of downvotes") int downvotes,
            @Schema(description = "Number of confirmations") int confirmations,
            @Schema(description = "Computed credibility score") int credibilityScore
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
    public record UserVoteResponse(@Schema(description = "true if the authenticated user has an active vote") boolean hasVoted) {}
}
