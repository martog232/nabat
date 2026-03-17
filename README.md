# Nabat - Real-Time Safety Alert Platform

    A simple early-stage Spring Boot project for safety alerts with PostgreSQL, REST APIs, JWT auth, and WebSocket notifications.

## What changed

This project now uses **one runtime configuration**.

- No `dev` / `prod` split for the app
- No profile switching needed for normal development
- One PostgreSQL setup
- One Docker Compose file
- Tests still use H2 internally, but **without a Spring profile**

## Tech stack

- Java 21
- Spring Boot 3.4.1
- Maven Wrapper (`mvnw.cmd`)
- PostgreSQL 16
- H2 for tests only
- WebSocket for realtime alerts

## Runtime defaults

The app expects this database by default:

- Host: `127.0.0.1`
- Port: `5432`
- Database: `nabat_db`
- Username: `nabat_user`
- Password: `nabat_password`

These defaults live in `src/main/resources/application.properties`.

## Quick start

### Option 1: Run everything with Docker

This starts:
- PostgreSQL
- the Spring Boot app

```powershell
cd C:\Users\MONI\IdeaProjects\nabat
docker compose up --build
```

App URLs:
- API: `http://localhost:8080`
- Health: `http://localhost:8080/actuator/health`
- WebSocket: `ws://localhost:8080/ws/alerts?userId=<your-user-id>`

Stop everything:

```powershell
cd C:\Users\MONI\IdeaProjects\nabat
docker compose down
```

Reset the database volume too:

```powershell
cd C:\Users\MONI\IdeaProjects\nabat
docker compose down -v
```

### Option 2: Run app locally, database in Docker

Start only PostgreSQL:

```powershell
cd C:\Users\MONI\IdeaProjects\nabat
docker compose up -d postgres
```

Run the app locally:

```powershell
cd C:\Users\MONI\IdeaProjects\nabat
.\mvnw.cmd spring-boot:run
```

### Option 3: Run app locally with a local PostgreSQL installation

Create the database:

```powershell
cd C:\Users\MONI\IdeaProjects\nabat
setup-db.bat
```

Then run the app:

```powershell
cd C:\Users\MONI\IdeaProjects\nabat
.\mvnw.cmd spring-boot:run
```

## Testing

Run tests:

```powershell
cd C:\Users\MONI\IdeaProjects\nabat
.\mvnw.cmd test
```

Notes:
- Tests use an in-memory H2 database
- Tests do not need a Spring profile
- Tests do not need PostgreSQL running

## WebSocket

Connect directly to:

```text
ws://localhost:8080/ws/alerts?userId=<your-user-id>
```

## Docker services

`docker-compose.yml` contains only:
- `postgres`
- `nabat-app`

The WebSocket endpoint is served by the Spring Boot app itself.

## Build a JAR

```powershell
cd C:\Users\MONI\IdeaProjects\nabat
.\mvnw.cmd clean package
```

Run it:

```powershell
java -jar target\nabat-0.0.1-SNAPSHOT.jar
```

## Troubleshooting

### Port 5432 already in use

Another PostgreSQL instance may already be running.
Stop it, or change the datasource URL in `application.properties`.

On Windows, this project uses `127.0.0.1` instead of `localhost` to avoid IPv6/WSL port-routing issues.

### Port 8080 already in use

Run the app on another port:

```powershell
cd C:\Users\MONI\IdeaProjects\nabat
.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"
```

### Reset Docker database

```powershell
cd C:\Users\MONI\IdeaProjects\nabat
docker compose down -v
docker compose up --build
```

## Project structure

```text
src/main/java/org/example/nabat/
├── domain/
├── application/
├── adapter/
└── config/
```

## Documentation

- `LOCAL_DB_SETUP.md`
- `DATABASE_SETUP.md`

The project is intentionally kept simple for early development.
