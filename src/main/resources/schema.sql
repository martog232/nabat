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