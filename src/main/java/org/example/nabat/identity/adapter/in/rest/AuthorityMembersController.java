package org.example.nabat.identity.adapter.in.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.example.nabat.identity.application.port.in.AuthorityMembersUseCase;
import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * An authority's officials, managed by its own account — the one that signs in with its
 * mailbox. 403 for any other account, an official of the same authority included.
 */
@RestController
@RequestMapping("/api/v1/authority/members")
@PreAuthorize("hasRole('OFFICIAL')")
public class AuthorityMembersController {

    private final AuthorityMembersUseCase members;

    public AuthorityMembersController(AuthorityMembersUseCase members) {
        this.members = members;
    }

    @GetMapping
    @Operation(summary = "Everyone acting for the caller's authority")
    public ResponseEntity<List<UserResponse>> list(@AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(members.members(currentUser).stream().map(UserResponse::from).toList());
    }

    @PostMapping
    @Operation(summary = "Invite a colleague: an official account of their own, and a mail to set its password")
    public ResponseEntity<UserResponse> invite(
        @Valid @RequestBody InviteRequest request,
        @AuthenticationPrincipal User currentUser
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(UserResponse.from(members.invite(currentUser, request.email(), request.displayName())));
    }

    @DeleteMapping("/{userId}")
    @Operation(summary = "Take the official role from a colleague who has left")
    public ResponseEntity<UserResponse> remove(@PathVariable UUID userId, @AuthenticationPrincipal User currentUser) {
        return ResponseEntity.ok(UserResponse.from(members.remove(currentUser, UserId.of(userId))));
    }

    @Schema(description = "A colleague to invite")
    public record InviteRequest(
        @Schema(example = "ivan.petrov@lozenets.example") @NotBlank @Email @Size(max = 255) String email,
        @Schema(example = "Иван Петров") @NotBlank @Size(max = 100) String displayName
    ) {
    }
}
