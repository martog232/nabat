package org.example.nabat.incident.adapter.in.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.example.nabat.identity.domain.User;
import org.example.nabat.incident.application.port.in.CommentOnReportUseCase;
import org.example.nabat.incident.domain.AlertId;
import org.example.nabat.incident.domain.Comment;
import org.example.nabat.incident.domain.CommentRemoval;
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
import java.util.List;
import java.util.UUID;

/**
 * A report's thread. {@code POST} adds to it while the report is open — 409
 * {@code COMMENTS_LOCKED} once it is resolved or rejected; {@code DELETE} takes a comment down,
 * for its author while the report is open and for a moderator at any time.
 */
@RestController
@RequestMapping("/api/v1/alerts/{id}/comments")
public class CommentController {

    private final CommentOnReportUseCase comments;

    public CommentController(CommentOnReportUseCase comments) {
        this.comments = comments;
    }

    @GetMapping
    @Operation(summary = "A report's comments, oldest first")
    public ResponseEntity<List<CommentResponse>> thread(@PathVariable UUID id, @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(comments.thread(AlertId.of(id)).stream()
            .map(c -> CommentResponse.from(c, currentUser))
            .toList());
    }

    @PostMapping
    @Operation(summary = "Comment on an open report")
    public ResponseEntity<CommentResponse> add(
        @PathVariable UUID id,
        @Valid @RequestBody CommentRequest request,
        @AuthenticationPrincipal User currentUser
    ) {
        Comment added = comments.add(AlertId.of(id), currentUser, request.body());
        return ResponseEntity.status(HttpStatus.CREATED).body(CommentResponse.from(added, currentUser));
    }

    @DeleteMapping("/{commentId}")
    @Operation(summary = "Take a comment down")
    public ResponseEntity<CommentResponse> remove(
        @PathVariable UUID id,
        @PathVariable UUID commentId,
        @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.ok(CommentResponse.from(comments.remove(AlertId.of(id), commentId, currentUser), currentUser));
    }

    @Schema(description = "A comment to add")
    public record CommentRequest(
        @Schema(description = "The text, 1 to 2000 characters", example = "Още е там, и е по-дълбока след дъжда.")
        @NotBlank(message = "Comment is required")
        @Size(max = Comment.MAX_LENGTH, message = "A comment is at most 2000 characters")
        String body
    ) {
    }

    @Schema(description = "One comment in a report's thread")
    public record CommentResponse(
        UUID id,
        @Schema(description = "The author's display name when they wrote it") String authorName,
        @Schema(description = "Written by an official of the report's own district") boolean byDistrict,
        @Schema(description = "Written by the person who reported it") boolean byReporter,
        @Schema(description = "Written by the caller") boolean mine,
        @Schema(description = "The text; null once taken down") String body,
        Instant createdAt,
        @Schema(description = "Who took it down — AUTHOR or MODERATOR; null while it stands") CommentRemoval removedBy
    ) {
        static CommentResponse from(Comment c, User caller) {
            return new CommentResponse(c.id(), c.authorName(), c.byDistrict(), c.byReporter(),
                caller != null && c.isBy(caller.id().value()), c.body(), c.createdAt(), c.removedBy());
        }
    }
}
