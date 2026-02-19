# Local PostgreSQL Setup Guide

This guide explains how to set up a local PostgreSQL database for Nabat development.

---

## Prerequisites

Install **PostgreSQL 16** for your operating system:

- **Windows**: Download from https://www.postgresql.org/download/windows/
- **macOS**: `brew install postgresql@16` or download from https://www.postgresql.org/download/macosx/
- **Linux (Ubuntu/Debian)**: `sudo apt install postgresql-16`
- **Linux (RHEL/Fedora)**: `sudo dnf install postgresql16-server`

After installation, ensure the `psql` command-line tool is available on your `PATH`.

---

## Option 1: Automated Setup Scripts

### Windows

Run the included batch script from the project root:

```bat
setup-db.bat
```

This script checks for `psql`, then runs `setup-db.sql` against the local PostgreSQL instance.

### macOS / Linux

```bash
psql -U postgres -f setup-db.sql
```

---

## Option 2: Manual Setup

Connect to PostgreSQL as the superuser and run the following commands:

```sql
CREATE DATABASE nabat_db;
CREATE USER nabat_user WITH PASSWORD 'nabat_password';
GRANT ALL PRIVILEGES ON DATABASE nabat_db TO nabat_user;
\c nabat_db
GRANT ALL ON SCHEMA public TO nabat_user;
GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO nabat_user;
GRANT ALL PRIVILEGES ON ALL SEQUENCES IN SCHEMA public TO nabat_user;
```

Or run the provided SQL script:

```bash
psql -U postgres -f setup-db.sql
```

---

## Connection Details

| Property  | Value            |
|-----------|------------------|
| Host      | `localhost`      |
| Port      | `5432`           |
| Database  | `nabat_db`       |
| Username  | `nabat_user`     |
| Password  | `nabat_password` |

These match the defaults in `application.properties`.

---

## Verify the Setup

After running the setup, verify the connection:

```bash
psql -U nabat_user -d nabat_db -h localhost -c "\conninfo"
```

You should see output confirming the connection to `nabat_db` as `nabat_user`.

---

## Option 3: Docker Compose

If you have Docker installed, you can start the database without a local PostgreSQL installation:

```bash
# Start the database only (development)
docker-compose -f docker-compose.dev.yml up -d db

# Or start the full stack
docker-compose up -d
```

See `docker-compose.yml` and `docker-compose.dev.yml` for configuration details.

---

## Easier Alternative: H2 In-Memory Database (dev profile)

For development and running tests without PostgreSQL, use the built-in H2 in-memory database:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

Or set the profile in your IDE run configuration:

```
spring.profiles.active=dev
```

Tests already use H2 automatically via `spring.profiles.active=test`.

---

## Troubleshooting

### Port conflict (5432 already in use)

Another PostgreSQL instance or service may be using port 5432. Stop it first:

```bash
# Linux / macOS
sudo systemctl stop postgresql

# Windows (run as Administrator)
net stop postgresql-x64-16
```

Or change the port in `application.properties`:

```properties
spring.datasource.url=jdbc:postgresql://localhost:5433/nabat_db
```

### Authentication failure (password authentication failed)

Ensure the `nabat_user` was created with the correct password and that `pg_hba.conf` allows `md5` or `scram-sha-256` authentication for local connections.

On Linux, the default `pg_hba.conf` location is `/etc/postgresql/16/main/pg_hba.conf`. Make sure it contains:

```
host  all  all  127.0.0.1/32  scram-sha-256
```

After editing `pg_hba.conf`, reload PostgreSQL:

```bash
sudo systemctl reload postgresql
```

### psql: command not found

Ensure the PostgreSQL bin directory is on your `PATH`:

- **Windows**: Add `C:\Program Files\PostgreSQL\16\bin` to the system `PATH`
- **macOS (Homebrew)**: `export PATH="/opt/homebrew/opt/postgresql@16/bin:$PATH"`
- **Linux**: `export PATH="/usr/lib/postgresql/16/bin:$PATH"`
