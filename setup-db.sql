-- Quick setup script for PostgreSQL
-- Run this if the batch script doesn't work

-- Connect as postgres superuser first, then run:

-- Create database
CREATE DATABASE nabat_db;

-- Create user
CREATE USER nabat_user WITH PASSWORD 'nabat_password';

-- Grant privileges
GRANT ALL PRIVILEGES ON DATABASE nabat_db TO nabat_user;

-- Connect to nabat_db and grant schema privileges
\c nabat_db
GRANT ALL ON SCHEMA public TO nabat_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO nabat_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO nabat_user;

-- Verify
\du nabat_user
\l nabat_db

