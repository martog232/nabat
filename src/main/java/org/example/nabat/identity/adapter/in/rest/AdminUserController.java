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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
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

    /**
     * The largest page this endpoint will return, whatever is asked for.
     *
     * <p>A cap rather than trust: {@code size} arrives from a query string, and without one a
     * single request can ask the database for every account there has ever been and ship it
     * to a browser. The admin screen asks for 25.
     */
    private static final int MAX_PAGE_SIZE = 100;

    private final AdministerUsersUseCase administerUsersUseCase;

    /**
     * One page of accounts, newest first — what the admin screen lists.
     *
     * <p>The only endpoint here that reads rather than writes, and the reason the screen can
     * exist at all: the two PATCHes address an account by id, which is no use to someone who
     * does not already know the id.
     */
    @GetMapping
    public ResponseEntity<UserPageResponse> listUsers(
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size,
        @AuthenticationPrincipal User actor
    ) {
        AdministerUsersUseCase.UserPage found = administerUsersUseCase.listUsers(
            actor.id(), Math.max(page, 0), Math.clamp(size, 1, MAX_PAGE_SIZE));

        return ResponseEntity.ok(new UserPageResponse(
            found.users().stream().map(UserResponse::from).toList(),
            found.total()));
    }

    @Schema(description = "One page of accounts, with the total so a caller knows there are more")
    public record UserPageResponse(List<UserResponse> users, long total) {
    }

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
