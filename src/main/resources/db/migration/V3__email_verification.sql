-- T-45: Email verification & password reset

-- Add email_verified column to users
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Seed existing known-good test users as verified so seeds still work
UPDATE users SET email_verified = TRUE
WHERE id IN (
    '550e8400-e29b-41d4-a716-446655440000',
    '550e8400-e29b-41d4-a716-446655440001'
);

-- Verification tokens table (covers both email-verification and password-reset tokens)
CREATE TABLE IF NOT EXISTS verification_tokens (
    id          VARCHAR(36)  PRIMARY KEY,
    user_id     UUID         NOT NULL,
    type        VARCHAR(30)  NOT NULL,
    expires_at  TIMESTAMP    NOT NULL,
    used        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vt_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_vt_user_type ON verification_tokens(user_id, type);

