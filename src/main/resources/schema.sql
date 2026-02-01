-- Database schema for Nabat safety alert platform

-- Drop tables if they exist (for clean setup)
DROP TABLE IF EXISTS user_subscriptions CASCADE;
DROP TABLE IF EXISTS alerts CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- Users table
CREATE TABLE users (
    id UUID PRIMARY KEY,
    username VARCHAR(100) NOT NULL UNIQUE,
    email VARCHAR(255) NOT NULL UNIQUE,
    phone VARCHAR(20),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_seen TIMESTAMP,
    is_active BOOLEAN NOT NULL DEFAULT TRUE
);

-- Alerts table
CREATE TABLE alerts (
    id UUID PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    type VARCHAR(50) NOT NULL,
    severity VARCHAR(50) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMP NOT NULL,
    status VARCHAR(50) NOT NULL,
    reported_by UUID NOT NULL,
    resolved_at TIMESTAMP,
    CONSTRAINT fk_reported_by FOREIGN KEY (reported_by) REFERENCES users(id) ON DELETE CASCADE
);

-- User subscriptions table
CREATE TABLE user_subscriptions (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    alert_type VARCHAR(50) NOT NULL,
    latitude DOUBLE PRECISION NOT NULL,
    longitude DOUBLE PRECISION NOT NULL,
    radius_km DOUBLE PRECISION NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_subscription FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT unique_user_alert_subscription UNIQUE (user_id, alert_type, latitude, longitude)
);

-- Indexes for performance
CREATE INDEX idx_alerts_status ON alerts(status);
CREATE INDEX idx_alerts_type ON alerts(type);
CREATE INDEX idx_alerts_created_at ON alerts(created_at DESC);
CREATE INDEX idx_alerts_location ON alerts(latitude, longitude);
CREATE INDEX idx_alerts_reported_by ON alerts(reported_by);

CREATE INDEX idx_user_subscriptions_user_id ON user_subscriptions(user_id);
CREATE INDEX idx_user_subscriptions_alert_type ON user_subscriptions(alert_type);
CREATE INDEX idx_user_subscriptions_location ON user_subscriptions(latitude, longitude);
CREATE INDEX idx_user_subscriptions_active ON user_subscriptions(is_active);

--Alert votes table
CREATE TABLE IF NOT EXISTS alert_votes (
                                           id UUID PRIMARY KEY,
                                           alert_id UUID NOT NULL,
                                           user_id UUID NOT NULL,
                                           vote_type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_alert_votes_alert FOREIGN KEY (alert_id) REFERENCES alerts(id) ON DELETE CASCADE,
    CONSTRAINT fk_alert_votes_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_alert_votes_alert_user UNIQUE (alert_id, user_id)
    );

-- Добави колони към alerts (ако не съществуват)
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS upvote_count INTEGER DEFAULT 0;
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS downvote_count INTEGER DEFAULT 0;
ALTER TABLE alerts ADD COLUMN IF NOT EXISTS confirmation_count INTEGER DEFAULT 0;



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