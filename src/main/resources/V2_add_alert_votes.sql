CREATE TABLE alert_votes (
                             id UUID PRIMARY KEY,
                             alert_id UUID NOT NULL,
                             user_id UUID NOT NULL,
                             vote_type VARCHAR(20) NOT NULL,  -- UPVOTE, DOWNVOTE, CONFIRM
                             created_at TIMESTAMP NOT NULL,

    -- Foreign keys
                             CONSTRAINT fk_alert_votes_alert FOREIGN KEY (alert_id) REFERENCES alerts(id) ON DELETE CASCADE,
                             CONSTRAINT fk_alert_votes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,

    -- Един потребител може да гласува веднъж за alert
                             CONSTRAINT uk_alert_votes_alert_user UNIQUE (alert_id, user_id)
);

-- Индекси за бързо търсене
CREATE INDEX idx_alert_votes_alert_id ON alert_votes(alert_id);
CREATE INDEX idx_alert_votes_user_id ON alert_votes(user_id);
CREATE INDEX idx_alert_votes_type ON alert_votes(alert_id, vote_type);

-- ═══════════════════════════════════════════════════════════
-- Добавяне на vote counts към alerts таблицата
-- ═══════════════════════════════════════════════════════════

ALTER TABLE alerts ADD COLUMN upvote_count INTEGER DEFAULT 0;
ALTER TABLE alerts ADD COLUMN downvote_count INTEGER DEFAULT 0;
ALTER TABLE alerts ADD COLUMN confirmation_count INTEGER DEFAULT 0;