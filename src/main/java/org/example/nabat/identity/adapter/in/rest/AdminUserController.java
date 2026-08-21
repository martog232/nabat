package org.example.nabat.identity.adapter.in.rest;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.example.nabat.identity.application.port.in.AdministerUsersUseCase;
import org.example.nabat.identity.domain.Role;
import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Account administration. Admin-only, and separate from {@link UserController} because the
 * subject is a different account than the caller's own.
 *
 * <p>Two gates, deliberately. {@code @PreAuthorize} rejects a caller whose token does not
 * claim the role, which keeps unauthorised requests out of the application layer entirely.
 * The use case then re-reads the actor and checks the current row, because a token carries
 * the role it was minted with — an admin demoted a minute ago still presents
 * {@code ROLE_ADMIN} until that token expires.
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final AdministerUsersUseCase administerUsersUseCase;

    @PatchMapping("/{id}/role")
    public ResponseEntity<UserResponse> changeRole(
        @PathVariable UUID id,
        @Valid @RequestBody ChangeRoleRequest request,
        @AuthenticationPrincipal User actor
    ) {
        User updated = administerUsersUseCase.changeRole(actor.id(), UserId.of(id), request.role());
        return ResponseEntity.ok(UserResponse.from(updated));
    }

    @PatchMapping("/{id}/enabled")
    public ResponseEntity<UserResponse> setEnabled(
        @PathVariable UUID id,
        @Valid @RequestBody SetEnabledRequest request,
        @AuthenticationPrincipal User actor
    ) {
        User updated = administerUsersUseCase.setEnabled(actor.id(), UserId.of(id), request.enabled());
        return ResponseEntity.ok(UserResponse.from(updated));
    }

    @Schema(description = "Assigns a role to an account")
    public record ChangeRoleRequest(
        @Schema(description = "The role to assign", example = "MODERATOR")
        @NotNull(message = "Role is required")
        Role role
    ) {
    }

    @Schema(description = "Enables or disables an account")
    public record SetEnabledRequest(
        @Schema(
            description = "False disables the account and invalidates its sessions immediately",
            example = "false"
        )
        @NotNull(message = "enabled is required")
        Boolean enabled
    ) {
    }
}
