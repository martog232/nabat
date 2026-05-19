# Nabat — Real-Time Safety Alert Platform

A Spring Boot service for crowd-sourced safety alerts: users report incidents tied to a GPS location, vote on each other's reports, and receive real-time pushes over WebSocket. Backed by PostgreSQL, secured with JWT.

> The web client previously hosted in `frontend/` now lives in a separate repository: [martog232/nabat-fe](https://github.com/martog232/nabat-fe). This repository contains the backend only.

> Status: early-stage. Auth, alerts, voting and a WebSocket broadcast skeleton work end-to-end. Notifications domain and per-user subscriptions are scaffolded but not wired up yet (see [Roadmap](#roadmap)).

---

## Tech stack

- Java 21, Spring Boot 3.4.1
- Spring Web, Spring Data JPA, Spring Security, Spring WebSocket, Spring Validation, Spring Actuator
- PostgreSQL 16 (production & development), H2 in PostgreSQL-mode (tests only — no Spring profile needed)
- Flyway for schema + seed data
- `io.jsonwebtoken:jjwt` for token signing/parsing
- Lombok
- Maven Wrapper (`mvnw.cmd` / `mvnw`)
- JaCoCo (60% line coverage threshold), Checkstyle (non-failing)

## Architecture

Hexagonal / ports-and-adapters. Package layout under `org.example.nabat`:

```text
src/main/java/org/example/nabat/
├── domain/
│   ├── model/         # records: Alert, AlertVote, User, Location, Notification, value-object IDs, enums
│   └── exception/
├── application/
│   ├── UseCase.java   # custom @stereotype, picked up by component scan
│   ├── port/
│   │   ├── in/        # use-case interfaces (CreateAlert, VoteAlert, AuthN, ...)
│   │   └── out/       # driven ports (AlertRepository, TokenProvider, AlertNotificationPort, ...)
│   └── service/       # use-case implementations
├── adapter/
│   ├── in/
│   │   ├── rest/      # @RestControllers + DTOs + GlobalExceptionHandler
│   │   ├── security/  # SecurityConfig, JwtAuthenticationFilter, JwtTokenProvider
│   │   └── websocket/ # AlertWebSocketHandler
│   └── out/
│       ├── persistence/   # JPA entities + adapters implementing out-ports
│       └── notification/  # WebSocket-backed AlertNotificationPort, log-only NotificationSender
└── config/            # WebSocketConfig, UseCaseConfig
```

Domain types are pure Java records with no framework annotations. Adapters translate to/from `*JpaEntity` classes via static `from(...)` / `toDomain()` methods.

## Authentication

JWT-based authentication with role support. The system uses two token types:

- **Access token** (24h default) — for API requests via `Authorization: Bearer <token>`
- **Refresh token** (7d default) — for obtaining new access/refresh token pairs via `/auth/refresh`

### Security features

- Access tokens carry `userId`, `email`, `role`, and `tokenType` claims
- **Token type enforcement** — refresh tokens are rejected if used for API authentication (only access tokens allowed)
- **User lookup by stable ID** — authentication uses `userId` from token (not `email`, which can change)
- **Role-based authorities** — JWT filter sets Spring Security authorities as `ROLE_<role>` (e.g., `ROLE_USER`, `ROLE_ADMIN`)
- **Disabled user check** — authentication skipped if user exists but is disabled
- **Security context cleanup** — on any authentication error, context is cleared to prevent leaks

### Environment variables

| Variable | Default | Notes |
| --- | --- | --- |
| `JWT_SECRET` | local-dev fallback | Override outside local development. Must be ≥ 32 chars and must not contain `change-me-before-production`. |
| `jwt.expiration` | `86400000` (24h) | Access token lifetime (ms) |
| `jwt.refresh-expiration` | `604800000` (7d) | Refresh token lifetime (ms) |

### Endpoints

- `POST /api/v1/auth/register` — returns tokens + user (auto-login)
- `POST /api/v1/auth/login` — `{ email, password }` → tokens + user
- `POST /api/v1/auth/refresh` — `{ refreshToken }` → new access/refresh pair
- `GET /api/v1/auth/me` — current authenticated user

Seeded users (dev only — insecure bcrypt hashes):

- `test@example.com`
- `admin@example.com`

## Runtime configuration

Single configuration — no `dev` / `prod` profile split. All knobs are environment variables with sensible defaults in `src/main/resources/application.properties`:

| Env var | Default | Notes |
| --- | --- | --- |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://127.0.0.1:5432/nabat_db` | Use `127.0.0.1` to avoid Windows IPv6 issues. If using Docker Postgres from the quick start, use port `5433`. |
| `SPRING_DATASOURCE_USERNAME` | `nabat_user` | |
| `SPRING_DATASOURCE_PASSWORD` | `nabat_password` | |
| `SERVER_PORT` | `8080` | |
| `JWT_SECRET` | local-dev fallback | Override outside local development. Must be ≥ 32 chars and must not contain `change-me-before-production`. |
| `jwt.expiration` | `86400000` (24h) | Access-token lifetime, ms |
| `jwt.refresh-expiration` | `604800000` (7d) | Refresh-token lifetime, ms |

Tests use H2 in PostgreSQL compatibility mode (`src/test/resources/application.properties`) — no PostgreSQL required.

## Quick start

### Option 1 — Everything in Docker

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Brings up `postgres` + `nabat-app`. App will be on http://localhost:8080.

Stop & wipe data:

```powershell
docker compose down -v
```

### Option 2 — App locally, DB in Docker

```powershell
docker compose up -d postgres
$env:SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:5433/nabat_db"
$env:SPRING_DATASOURCE_USERNAME="nabat_user"
$env:SPRING_DATASOURCE_PASSWORD="nabat_password"
.\mvnw.cmd spring-boot:run
```

In IntelliJ IDEA, add these environment variables to your Spring Boot run configuration if you use the Docker database:

```text
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5433/nabat_db;SPRING_DATASOURCE_USERNAME=nabat_user;SPRING_DATASOURCE_PASSWORD=nabat_password
```

### Option 3 — Local PostgreSQL

```powershell
setup-db.bat
.\mvnw.cmd spring-boot:run
```

## REST API

All endpoints live under `/api/v1`. `/auth/**` is open; everything else under `/api/v1` requires `Authorization: Bearer <accessToken>`.

| Method | Path | Notes |
| --- | --- | --- |
| `POST` | `/api/v1/auth/register` | Returns tokens + user (auto-login) |
| `POST` | `/api/v1/auth/login` | `{ email, password }` → tokens |
| `POST` | `/api/v1/auth/refresh` | `{ refreshToken }` → new pair |
| `GET`  | `/api/v1/auth/me` | Current user |
| `POST` | `/api/v1/alerts` | Create alert |
| `GET`  | `/api/v1/alerts/nearby?latitude=&longitude=&radiusKm=5.0` | Haversine search |
| `POST` | `/api/v1/alerts/{alertId}/votes` | `{ voteType: UPVOTE \| DOWNVOTE \| CONFIRM }` |
| `DELETE` | `/api/v1/alerts/{alertId}/votes` | Remove current user's vote |
| `GET`  | `/api/v1/alerts/{alertId}/votes/stats` | Aggregate counts + credibility score |
| `GET`  | `/api/v1/alerts/{alertId}/votes/me` | Has the current user voted? |
| `GET`  | `/actuator/health`, `/actuator/info` | |

Errors use a uniform JSON envelope (`status`, `message`, `timestamp`); validation errors add an `errors` map. Mappings: `BadCredentialsException` → 401, `IllegalArgumentException` → 400, `IllegalStateException` → 409, `AlertNotFoundException` → 404.

### WebSocket

Browser clients should first exchange their access token for a short-lived one-time ticket:

```http
POST /api/v1/ws/tickets
Authorization: Bearer <accessToken>
```

Response:

```json
{
  "ticket": "<short-lived-ticket>",
  "expiresAt": "2026-05-18T12:00:00Z"
}
```

Then open the socket with that ticket:

```text
ws://127.0.0.1:8080/ws/alerts?ticket=<short-lived-ticket>
```

Non-browser clients may instead send `Authorization: Bearer <accessToken>` on the initial upgrade request.

Server pushes JSON frames `{ "type": "NEW_ALERT", "alert": { ... } }` to authenticated users when a matching alert is created.

> ℹ️ WebSocket tickets are currently stored in-memory, so they are one-time and node-local. If you later run multiple backend instances, back this with a shared store.

## Database & migrations

Schema and seed data are managed by Flyway under `src/main/resources/db/migration` (`V1__schema.sql`, `V2__seed_data.sql`). On boot the app runs migrations against the configured datasource; `spring.jpa.hibernate.ddl-auto=validate` ensures entities and schema agree.

Seeded users (dev only — passwords are placeholder bcrypt hashes; reset before any real use):

- `test@example.com`
- `admin@example.com`

## Testing

```powershell
.\mvnw.cmd test
```

- Unit tests: services + domain (Mockito).
- Slice tests: `@WebMvcTest` for controllers (security filters disabled).
- Integration tests: `@SpringBootTest` with H2 in PostgreSQL-mode — no PostgreSQL required.
- Coverage report: `target/site/jacoco/index.html`. JaCoCo is configured to fail the build below 60% line coverage (BUNDLE).

Run a single test:

```powershell
.\mvnw.cmd "-Dtest=AlertVoteServiceTest" test
```

## Build

```powershell
.\mvnw.cmd clean package
& "$env:JAVA_HOME\bin\java.exe" -jar target\nabat-0.0.1-SNAPSHOT.jar
```

The Docker image (`Dockerfile`) is multi-stage on Eclipse Temurin 21, runs as the non-root `spring` user, and exposes a `wget` health-check against `/actuator/health`.

## Troubleshooting

- **`jwt.secret still contains the dev placeholder marker 'change-me-before-production'`** — you are still running with an old placeholder value, likely from an IntelliJ environment variable. Remove that env var or set `JWT_SECRET` to a value that does not contain `change-me-before-production`. For PowerShell:
  ```powershell
  $env:JWT_SECRET="nabat-local-dev-jwt-secret-key-min-256-bits-for-local-development-only-123456"
  .\mvnw.cmd spring-boot:run
  ```
- **`FATAL: password authentication failed for user "nabat_user"`** — your app is connecting to a PostgreSQL instance that does not have the expected `nabat_user` / `nabat_password` credentials. For the Docker dev DB, use host port `5433`:
  ```powershell
  docker compose down
  docker compose up -d postgres
  $env:SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:5433/nabat_db"
  $env:SPRING_DATASOURCE_USERNAME="nabat_user"
  $env:SPRING_DATASOURCE_PASSWORD="nabat_password"
  $env:JWT_SECRET="nabat-local-dev-jwt-secret-key-min-256-bits-for-local-development-only-123456"
  .\mvnw.cmd spring-boot:run
  ```
- **Port 5432 in use** — another local Postgres is running. Stop it or change `SPRING_DATASOURCE_URL`.
- **Port 8080 in use** — `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.arguments=--server.port=8081"`.
- **`UnsupportedClassVersionError` / `class file version 61.0`** — your `java` command is older than Java 17/21. This project targets Java 21. Check with:
  ```powershell
  java -version
  echo $env:JAVA_HOME
  where.exe java
  ```
  If `java -version` prints Java 8, run the jar with Java 21 explicitly:
  ```powershell
  & "$env:JAVA_HOME\bin\java.exe" -jar target\nabat-0.0.1-SNAPSHOT.jar
  ```
- **Reset Docker DB** — `docker compose down -v && docker compose up --build`.
- **`localhost` resolution oddities on Windows/WSL** — this project intentionally uses `127.0.0.1` everywhere.

## Roadmap

Tracked gaps (see source for `TODO` markers):

### Functional
- [ ] **Authorize `POST /alerts`** off the `Authorization` header instead of trusting `reportedBy` in the request body.
- [ ] **Notifications end-to-end** — `NotificationService.sendVoteNotification` / `sendMilestoneNotification` are stubs returning `null`; no REST controller wired for `GetNotificationUseCase`; `AlertVoteService.vote` does not yet emit notifications.
- [ ] **User subscriptions** — `SubscribeToAlertsUseCase` interface exists with no implementation; `InMemoryUserSubscriptionRepository` always returns an empty list, so WebSocket broadcasts never fan out. The `user_subscriptions` table is seeded but unused.
- [ ] **Alert lifecycle** — `AlertStatus.RESOLVED` and `resolved_at` exist with no endpoint to resolve an alert; `GET /alerts/{id}` and `findByStatus` are unused.
- [ ] **Role-based authorization** — `Role.ADMIN` exists with a seeded admin user but nothing checks it. No `@PreAuthorize` anywhere.

### Hardening
- [ ] Lock down CORS origins (currently `*`) and externalize via property.
- [ ] Fail-fast when `JWT_SECRET` is unset in non-dev environments.
- [ ] Add `@PreAuthorize` / method-security and admin-only endpoints.
- [ ] Tighten `GlobalExceptionHandler` — don't leak `IllegalArgumentException` messages verbatim.
- [ ] Decide on idempotent vote semantics (re-voting same type currently → 409).

### Tests
- [ ] `NotificationService`, `JwtAuthenticationFilter`, `GlobalExceptionHandler`, `AlertWebSocketHandler`.
- [ ] Persistence-layer tests for the native Haversine query.
- [ ] Integration happy-path tests for `AlertVoteController`.
- [ ] Refresh-token edge cases (expired, access-token-as-refresh).

### DX / docs
- [ ] Add `springdoc-openapi` for an OpenAPI / Swagger UI spec.
- [ ] Add `.env.example` documenting `JWT_SECRET`, `SPRING_DATASOURCE_*`, `SERVER_PORT`.
- [ ] Translate any remaining inline Bulgarian comments to English.
