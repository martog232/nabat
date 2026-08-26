package org.example.nabat.identity.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The capability table, asserted rather than described.
 *
 * <p>Cheap to write and the reason it exists is specific: adding a role is a two-line change
 * to the enum and a silent change to every authorisation decision in the application. This
 * fails when a new role answers a capability nobody meant to grant it.
 */
class RoleTest {

    @Test
    void userCanNeitherModerateNorAdminister() {
        assertFalse(Role.USER.canModerateContent());
        assertFalse(Role.USER.canAdministerUsers());
    }

    @Test
    void moderatorCanModerateContentButNotAdministerUsers() {
        assertTrue(Role.MODERATOR.canModerateContent());
        // The whole point of the role. A moderator who can also hand out roles is an admin.
        assertFalse(Role.MODERATOR.canAdministerUsers());
    }

    @Test
    void adminCanDoBoth() {
        assertTrue(Role.ADMIN.canModerateContent());
        assertTrue(Role.ADMIN.canAdministerUsers());
    }

    /**
     * Guards the CHECK constraint in V12, which lists these names as strings. A constant
     * added to the enum and not to the migration fails on the first attempt to assign it —
     * in production, at a point far from this change.
     */
    @Test
    void theRoleSetIsExactlyWhatTheDatabaseConstraintAllows() {
        assertArrayEquals(
            new Role[] {Role.USER, Role.MODERATOR, Role.ADMIN},
            Role.values(),
            "Adding or reordering roles means updating the CHECK constraint on users.role"
        );
    }
}
