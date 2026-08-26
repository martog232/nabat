package org.example.nabat.identity.application;

import org.example.nabat.identity.application.port.out.UserRepository;
import org.example.nabat.identity.domain.Role;
import org.example.nabat.identity.domain.User;
import org.example.nabat.identity.domain.UserId;
import org.example.nabat.identity.domain.UserNotFoundException;
import org.example.nabat.shared.domain.NotAuthorizedException;
import org.example.nabat.testsupport.Fixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserAdministrationServiceTest {

    @Mock
    private UserRepository userRepository;

    private UserAdministrationService service;
    private User admin;
    private User plainUser;

    @BeforeEach
    void setUp() {
        service = new UserAdministrationService(userRepository);
        admin = Fixtures.admin();
        plainUser = Fixtures.user("target@example.com");
    }

    private void given(User... users) {
        for (User u : users) {
            when(userRepository.findById(u.id())).thenReturn(Optional.of(u));
        }
        when(userRepository.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
    }

    @Test
    void adminPromotesAUserToModerator() {
        given(admin, plainUser);

        User updated = service.changeRole(admin.id(), plainUser.id(), Role.MODERATOR);

        assertEquals(Role.MODERATOR, updated.role());
        assertTrue(updated.role().canModerateContent());
        assertFalse(updated.role().canAdministerUsers());
    }

    /**
     * Idempotent, not a conflict: the caller asked for a state that already holds, and
     * answering 409 would make a retry after a timeout look like a failure.
     */
    @Test
    void assigningTheRoleAUserAlreadyHoldsChangesNothing() {
        when(userRepository.findById(admin.id())).thenReturn(Optional.of(admin));
        when(userRepository.findById(plainUser.id())).thenReturn(Optional.of(plainUser));

        User result = service.changeRole(admin.id(), plainUser.id(), Role.USER);

        assertSame(plainUser, result);
        verify(userRepository, never()).save(any());
    }

    /**
     * The demotion an installation cannot undo. There is no break-glass path, so the last
     * step has to be taken by somebody else.
     */
    @Test
    void anAdminCannotChangeTheirOwnRole() {
        when(userRepository.findById(admin.id())).thenReturn(Optional.of(admin));

        NotAuthorizedException thrown = assertThrows(
            NotAuthorizedException.class,
            () -> service.changeRole(admin.id(), admin.id(), Role.USER)
        );

        assertTrue(thrown.getMessage().contains("your own role"));
        verify(userRepository, never()).save(any());
    }

    @Test
    void anAdminCannotDisableTheirOwnAccount() {
        when(userRepository.findById(admin.id())).thenReturn(Optional.of(admin));

        assertThrows(
            NotAuthorizedException.class,
            () -> service.setEnabled(admin.id(), admin.id(), false)
        );
        verify(userRepository, never()).save(any());
    }

    /**
     * The current row decides, not the token. A moderator's access token claims
     * ROLE_MODERATOR and the controller's @PreAuthorize would already have refused it — this
     * covers the case where the role changed after the token was minted.
     */
    @Test
    void aModeratorMayNotAdministerUsers() {
        User moderator = Fixtures.user("mod@example.com").withRole(Role.MODERATOR);
        when(userRepository.findById(moderator.id())).thenReturn(Optional.of(moderator));

        assertThrows(
            NotAuthorizedException.class,
            () -> service.changeRole(moderator.id(), plainUser.id(), Role.ADMIN)
        );
    }

    @Test
    void disablingInvalidatesSessionsAlreadyInFlight() {
        given(admin, plainUser);
        int versionBefore = plainUser.tokenVersion();

        service.setEnabled(admin.id(), plainUser.id(), false);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertFalse(saved.getValue().enabled());
        assertEquals(
            versionBefore + 1,
            saved.getValue().tokenVersion(),
            "a disabled account must stop being usable now, not at token expiry"
        );
    }

    /**
     * Re-enabling deliberately leaves the token version alone: someone switched off and back
     * on within a minute should not also have every session dropped.
     */
    @Test
    void enablingDoesNotInvalidateSessions() {
        User disabled = plainUser.disable();
        given(admin, disabled);

        service.setEnabled(admin.id(), disabled.id(), true);

        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(saved.capture());
        assertTrue(saved.getValue().enabled());
        assertEquals(disabled.tokenVersion(), saved.getValue().tokenVersion());
    }

    @Test
    void rejectsAMissingTarget() {
        when(userRepository.findById(admin.id())).thenReturn(Optional.of(admin));
        UserId ghost = UserId.generate();
        when(userRepository.findById(ghost)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> service.changeRole(admin.id(), ghost, Role.USER));
    }

    @Test
    void rejectsAnAbsentRole() {
        assertThrows(
            IllegalArgumentException.class,
            () -> service.changeRole(admin.id(), plainUser.id(), null)
        );
        verifyNoInteractions(userRepository);
    }
}
