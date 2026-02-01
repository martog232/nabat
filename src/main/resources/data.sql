
-- Insert sample data for testing
INSERT INTO users (id, email, password, display_name, role, enabled, created_at, updated_at) VALUES
    ('550e8400-e29b-41d4-a716-446655440000', 'test@example.com', '$2a$10$dummyHashedPassword123456789', 'Test User', 'USER', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('550e8400-e29b-41d4-a716-446655440001', 'admin@example.com', '$2a$10$dummyHashedPassword123456789', 'Admin User', 'ADMIN', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- Sample alert
INSERT INTO alerts (id, title, description, type, severity, latitude, longitude, created_at, status, reported_by, upvote_count, downvote_count, confirmation_count) VALUES
    ('650e8400-e29b-41d4-a716-446655440000',
     'Traffic Accident',
     'Major accident on Main Street',
     'ACCIDENT',
     'HIGH',
     42.6977,
     23.3219,
     CURRENT_TIMESTAMP,
     'ACTIVE',
     '550e8400-e29b-41d4-a716-446655440000',
     0, 0, 0);

-- Sample subscription
INSERT INTO user_subscriptions (id, user_id, alert_type, latitude, longitude, radius_km, is_active) VALUES
    ('750e8400-e29b-41d4-a716-446655440000',
     '550e8400-e29b-41d4-a716-446655440000',
     'ACCIDENT',
     42.6977,
     23.3219,
     5.0,
     TRUE);

