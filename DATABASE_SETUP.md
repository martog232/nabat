# Nabat Database Setup Guide

## Quick Start with H2 (Development)

To run the application with H2 in-memory database:

```bash
gradlew.bat bootRun --args='--spring.profiles.active=dev'
```

Access H2 console at: http://localhost:8080/h2-console
- JDBC URL: `jdbc:h2:mem:nabat_db`
- Username: `sa`
- Password: (empty)

## PostgreSQL Setup (Production-like)

### Option 1: Using Docker (Recommended)

1. Start PostgreSQL and pgAdmin:
```bash
docker-compose up -d
```

2. Wait for PostgreSQL to be ready:
```bash
docker-compose ps
```

3. Run the application:
```bash
gradlew.bat bootRun
```

4. Access pgAdmin at http://localhost:5050
   - Email: admin@nabat.com
   - Password: admin

5. Stop the database:
```bash
docker-compose down
```

### Option 2: Local PostgreSQL Installation

1. Install PostgreSQL from https://www.postgresql.org/download/windows/

2. Create database and user:
```sql
CREATE DATABASE nabat_db;
CREATE USER nabat_user WITH PASSWORD 'nabat_password';
GRANT ALL PRIVILEGES ON DATABASE nabat_db TO nabat_user;
```

3. Run the schema script:
```bash
psql -U nabat_user -d nabat_db -f src/main/resources/schema.sql
```

4. Run the application:
```bash
gradlew.bat bootRun
```

## Database Schema

The application includes the following tables:

- **users** - User accounts and profiles
- **alerts** - Safety alerts with geolocation
- **user_subscriptions** - User notification preferences by alert type and location

## Configuration Profiles

- **dev** - H2 in-memory database, auto-creates schema
- **prod** - PostgreSQL with environment variables
- **default** - PostgreSQL on localhost:5432

Switch profiles using:
```bash
gradlew.bat bootRun --args='--spring.profiles.active=dev'
```

## Environment Variables (Production)

Set these environment variables for production deployment:

```
DATABASE_URL=jdbc:postgresql://your-host:5432/nabat_db
DATABASE_USERNAME=nabat_user
DATABASE_PASSWORD=your-secure-password
ALLOWED_ORIGINS=https://yourdomain.com
PORT=8080
```

## Sample Data

The schema includes sample test data:
- 2 test users
- 1 sample alert (traffic accident in Sofia)
- 1 sample subscription

## Troubleshooting

### Connection refused
- Ensure PostgreSQL is running: `docker-compose ps`
- Check port 5432 is not in use

### Schema not found
- For PostgreSQL: Run `schema.sql` manually
- For H2: Check `spring.sql.init.mode=always` in dev profile

### Hibernate DDL issues
- Development: Use `spring.jpa.hibernate.ddl-auto=create-drop`
- Production: Use `spring.jpa.hibernate.ddl-auto=validate`

