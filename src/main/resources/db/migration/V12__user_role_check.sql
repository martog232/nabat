-- Constrain users.role to the roles that exist.
--
-- The column has been VARCHAR(20) with a DEFAULT of 'USER' and nothing else since V1, so the
-- database accepted any string at all: 'ADMINN', 'admin', 'superuser'. Hibernate maps the
-- column to the Role enum, which means a row like that fails on *read* — the account becomes
-- unloadable rather than under-privileged, and it fails inside the authentication path where
-- the cause is least obvious. Same reasoning as the CHECK on notification_radius_km in V6:
-- the allow-list belongs where the data is, not only where the code is.
--
-- MODERATOR is added in this migration. Nothing is rewritten: every existing row is already
-- USER or ADMIN, and both remain legal.
--
-- The authority for this list is the Role enum in identity/domain. Adding a constant there
-- without adding it here produces a constraint violation on the first attempt to assign it.
ALTER TABLE users
    ADD CONSTRAINT users_role_check
    CHECK (role IN ('USER', 'MODERATOR', 'ADMIN'));
