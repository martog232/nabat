
-- Insert sample data for testing
INSERT INTO users (id, username, email, phone, is_active) VALUES
                                                              ('550e8400-e29b-41d4-a716-446655440000', 'test_user', 'test@example.com', '+1234567890', TRUE),
                                                              ('550e8400-e29b-41d4-a716-446655440001', 'admin_user', 'admin@example.com', '+1234567891', TRUE);

-- Sample alert
INSERT INTO alerts (id, title, description, type, severity, latitude, longitude, created_at, status, reported_by) VALUES
    ('650e8400-e29b-41d4-a716-446655440000',
     'Traffic Accident',
     'Major accident on Main Street',
     'ACCIDENT',
     'HIGH',
     42.6977,
     23.3219,
     CURRENT_TIMESTAMP,
     'ACTIVE',
     '550e8400-e29b-41d4-a716-446655440000');

-- Sample subscription
INSERT INTO user_subscriptions (id, user_id, alert_type, latitude, longitude, radius_km, is_active) VALUES
    ('750e8400-e29b-41d4-a716-446655440000',
     '550e8400-e29b-41d4-a716-446655440000',
     'ACCIDENT',
     42.6977,
     23.3219,
     5.0,
     TRUE);

