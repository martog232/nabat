package org.example.nabat.adapter.in.rest;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.example.nabat.application.port.in.VoteAlertUseCase;
import org.example.nabat.application.port.in.VoteAlertUseCase.VoteCommand;
import org.example.nabat.domain.model.AlertId;
import org.example.nabat.domain.model.AlertVote;
import org.example.nabat.domain.model.User;
import org.example.nabat.domain.model.VoteType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/alerts/{alertId}/votes")
public class AlertVoteController {

    private final VoteAlertUseCase voteAlertUseCase;

    @PostMapping
    public ResponseEntity<VoteResponse> vote(
            @PathVariable UUID alertId,
            @Valid @RequestBody VoteRequest request,
            @AuthenticationPrincipal User currentUser  // Взима логнатия потребител
    ) {
        VoteCommand command = new VoteCommand(
                AlertId.of(alertId),
                currentUser.id(),
                request.voteType()
        );

        AlertVote vote = voteAlertUseCase.vote(command);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(VoteResponse.from(vote));
    }

    // ═══════════════════════════════════════════════════════════
    // DELETE /api/v1/alerts/{alertId}/votes - Премахване на глас
    // ═══════════════════════════════════════════════════════════
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

    // ═══════════════════════════════════════════════════════════
    // GET /api/v1/alerts/{alertId}/votes/stats - Статистика
    // ═══════════════════════════════════════════════════════════
    @GetMapping("/stats")
    public ResponseEntity<VoteStatsResponse> getStats(@PathVariable UUID alertId) {
        VoteAlertUseCase.VoteStats stats = voteAlertUseCase.getVoteStats(AlertId.of(alertId));
        return ResponseEntity.ok(VoteStatsResponse.from(stats));
    }

    // ═══════════════════════════════════════════════════════════
    // GET /api/v1/alerts/{alertId}/votes/me - Моят глас
    // ═══════════════════════════════════════════════════════════
    @GetMapping("/me")
    public ResponseEntity<UserVoteResponse> getMyVote(
            @PathVariable UUID alertId,
            @AuthenticationPrincipal User currentUser
    ) {
        boolean hasVoted = voteAlertUseCase.hasUserVoted(
                AlertId.of(alertId),
                currentUser. id()
        );

        return ResponseEntity. ok(new UserVoteResponse(hasVoted));
    }

    public record VoteRequest(@NotNull VoteType voteType) {
    }

    public record VoteResponse(UUID id, UUID alertId, VoteType voteType, String createdAt) {
        public static VoteResponse from(AlertVote vote) {
            return new VoteResponse(
                    vote.id().value(),
                    vote.alertId().value(),
                    vote. voteType(),
                    vote.createdAt().toString()
            );
        }
    }

    public record VoteStatsResponse(
            int upvotes,
            int downvotes,
            int confirmations,
            int credibilityScore
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

    public record UserVoteResponse(boolean hasVoted) {}
}
