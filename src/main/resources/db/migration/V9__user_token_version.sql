-- Session invalidation support.
--
-- Access and refresh tokens carry the token_version they were minted at. The
-- authentication filter rejects any token whose version is behind the user's
-- current value, which is what makes a password reset (or an account being
-- disabled) actually end sessions that are already in flight. Previously a reset
-- left every issued token valid until its own expiry — up to 7 days for a refresh
-- token.
ALTER TABLE users
    ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0;
