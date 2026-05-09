# Local PostgreSQL Setup — Troubleshooting Guide

This file documents common issues you may encounter when running `setup-db.bat`
and how to resolve them.

---

## Pre-requisites

| Requirement | Minimum version |
|-------------|----------------|
| PostgreSQL  | 16             |
| `psql` on `PATH` | any |

Typical `psql` location on Windows:
```
C:\Program Files\PostgreSQL\16\bin
```

---

## Running the setup script

```bat
setup-db.bat
```

The script executes `setup-db.sql` as the `postgres` superuser and creates:

| Item     | Value           |
|----------|-----------------|
| Database | `nabat_db`      |
| User     | `nabat_user`    |
| Password | `nabat_password`|

---

## Common errors

### `psql` not found on PATH
Add the PostgreSQL `bin` directory to your system `PATH`:

1. Open **System Properties → Advanced → Environment Variables**.
2. Edit `Path` under *System variables*.
3. Add `C:\Program Files\PostgreSQL\16\bin` (adjust version as needed).
4. Restart your shell.

---

### Authentication failed for user "postgres"
PostgreSQL requires a password for the `postgres` superuser. Run:

```bat
psql -U postgres -W -f setup-db.sql
```

You will be prompted for the `postgres` password you chose during installation.

---

### PostgreSQL service not running
Start the service:

```powershell
Start-Service postgresql-x64-16
```

Or via **Services** (`services.msc`) → find **postgresql-x64-16** → **Start**.

---

### Port 5432 already in use
Check what is occupying the port:

```powershell
netstat -ano | findstr :5432
```

Either stop the conflicting process or change the PostgreSQL port in
`postgresql.conf` and update `spring.datasource.url` accordingly.

---

## Verify the connection

After running `setup-db.bat` successfully:

```bat
psql -U nabat_user -d nabat_db -h 127.0.0.1
```

Use `127.0.0.1` instead of `localhost` to avoid Windows IPv6 resolution issues.

---

## Docker shortcut (recommended for dev)

Skip the manual setup entirely and spin up a pre-configured Postgres container:

```powershell
docker compose up -d postgres
```

The container exposes Postgres on host port **5433** (to avoid conflicts with a
local install). Update your datasource URL when using Docker:

```
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/nabat_db
```

