# AGENTS.md — Nabat backend

Spring Boot 3.4 / Java 21 real-time safety-alert service. Hexagonal (ports & adapters) layout under `org.example.nabat`. Read `README.md` for product context; this file is the agent-facing cheat sheet.

---

## Architecture rules (non-obvious)

Layers under `src/main/java/org/example/nabat/`:

- `domain/model` — pure Java **records** + value-object IDs (`AlertId`, `UserId`, …) and enums. No Spring/JPA/Lombok annotations here. Domain logic lives on records (e.g. `Alert.create(...)`, `Alert.resolve()`).
- `application/port/in` — use-case interfaces (one method, plus a nested `Command` record when needed, e.g. `CreateAlertUseCase.CreateAlertCommand`).
- `application/port/out` — driven ports (`AlertRepository`, `AlertVoteRepository`, `UserSubscriptionRepository`, `AlertNotificationPort`, `TokenProvider`, …). Implemented in `adapter/out/**`.
- `application/service` — use-case implementations. **Always annotate with `@UseCase`** (custom stereotype in `application/UseCase.java`). Don't use `@Service` and don't register them in `@Configuration` classes; `@UseCase` is itself a `@Component`.
- `adapter/in/rest` — `@RestController` + request/response DTO records under the same package; cross-cutting errors handled by `GlobalExceptionHandler`.
- `adapter/in/security` — `SecurityConfig`, `JwtAuthenticationFilter`, `JwtTokenProvider`, `LoginAttemptTracker`.
- `adapter/in/websocket` — `AlertWebSocketHandler` + `JwtHandshakeInterceptor`. Browser clients **must** first call `POST /api/v1/ws/tickets` (authenticated REST) to obtain a short-lived one-time ticket, then open `ws://.../ws/alerts?ticket=<ticket>`. Non-browser clients may send `Authorization: Bearer <accessToken>` on the HTTP upgrade instead. The `JwtHandshakeInterceptor` handles both paths and stores the resolved `UUID` under `JwtHandshakeInterceptor.USER_ID_ATTR` in the session attributes. **Never trust `?userId=` from the client.**
- `adapter/out/persistence` — `*JpaEntity` (Lombok `@Getter/@Setter`, protected no-arg ctor) + `*JpaRepository` (Spring Data) + `*RepositoryAdapter` (`@Component` implementing the out-port). Migrations in `src/main/resources/db/migration/`.

Persistence is PostgreSQL with **Flyway**. `spring.jpa.hibernate.ddl-auto=validate` — never let Hibernate auto-create or update the schema.

---

## Implemented features (current state)

### WebSocket authentication (`adapter/in/websocket`)
- `JwtHandshakeInterceptor` validates on the HTTP upgrade. Two accepted auth paths:
  1. `Authorization: Bearer <accessToken>` header (non-browser clients).
  2. `?ticket=<one-time-ticket>` query param (browser clients). Tickets are issued by `POST /api/v1/ws/tickets` and redeemed via `RedeemWebSocketTicketUseCase` (backed by `WebSocketTicketService`). A ticket is single-use and short-lived.
- `AlertWebSocketHandler` reads `userId` exclusively from `session.getAttributes()` — never from query params.

### Spatial queries (`adapter/out/persistence` + migrations)
- `V4__postgis_spatial_indexes.sql` enables the `postgis` extension and adds a `GEOGRAPHY(Point, 4326)` column + GiST index on `alerts`. Nearby-alert queries use `ST_DWithin` instead of Haversine.
- Tests that exercise spatial queries use **Testcontainers** with a PostGIS image (`@DataJpaTest`). Docker is required for those tests.

### Credibility projection (CQRS light)
- `AlertVoteService` publishes a `VoteCastEvent` (Spring `ApplicationEventPublisher`) after each vote is persisted.
- `VoteCastProjectionUpdater` listens with `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async`. It re-counts votes and calls `AlertRepository.updateVoteCounts(alertId, upvotes, downvotes, confirmations, credibilityScore)`.
- `V5__alert_credibility_projection.sql` adds the four denormalised columns (`upvote_count`, `downvote_count`, `confirmation_count`, `credibility_score`) to the `alerts` table.
- Formula: `credibilityScore = upvotes - downvotes + (confirmations × 2)`. Same logic lives on `Alert.getCredibilityScore()` for in-memory use.

### Alert state machine (`domain/model`)
- `AlertStatus` enum: `ACTIVE`, `RESOLVED`.
- `Alert.resolve()` throws `IllegalStateException` ("Alert is already resolved") if already resolved. It is the **only** way to transition status — do not set status directly in the persistence adapter.
- `V1__initial_schema.sql` stores status as a VARCHAR with a CHECK constraint.

### Notification system (`application/service`)
- `NotificationService` — creates and persists `Notification` records; delivers them in real time via `AlertWebSocketHandler.sendNotificationToUser(...)` if the user is online, or marks them for later retrieval if offline.
- `NotificationMilestones` — defines the credibility-score thresholds that trigger milestone notifications (e.g. first confirmation, viral alert).
- `AlertVoteService` calls `NotificationService.sendVoteNotification` and `sendMilestoneNotification` after each vote.

### Subscription fan-out (`application/service`)
- `SubscriptionService` — manages `UserSubscription` records (user ↔ alert-type pairs).
- `CreateAlertService` calls `UserSubscriptionRepository.findUsersSubscribedToAlertType(type)` and fans out WebSocket pushes to all matching online users via `AlertWebSocketHandler.sendAlertToUser(...)`.

---

## Conventions

- **New use case** = new in-port interface in `port/in` + new `@UseCase` service in `application/service`. Wire only via constructor injection of out-ports.
- **New persistence type** = entity + Spring Data repo + adapter implementing the out-port. Adapters are `@Component`, not `@Repository`.
- **REST DTOs** are records co-located with controllers. Validate with `jakarta.validation`; `MethodArgumentNotValidException` maps to `{status, message, errors, timestamp}`.
- **Exceptions → HTTP**: `IllegalArgumentException` → 400, `IllegalStateException` → 409, `*NotFoundException` → 404, `BadCredentialsException` → 401, `AccessDeniedException` → 403. **Message is sent to the client.** Never throw Spring Security exceptions from domain or application layers.
- All HTTP routes are under `/api/v1`. `/api/v1/auth/**` is open; all other routes require `Authorization: Bearer <accessToken>`. JWT filter sets authorities as `ROLE_<role>`.
- **`POST /api/v1/alerts`** must extract `reportedBy` from the JWT principal (the `sub` claim), **not** from the request body. The request body field `reportedBy` should be removed or ignored.
- Config is env-var driven; **no Spring profiles**. Defaults in `application.properties`. `JWT_SECRET` ≥ 32 chars and must not contain `change-me-before-production`.
- CORS origins: `nabat.cors.allowed-origins` (comma-separated).

---

## Flyway migration history

| Version | Description |
|---------|-------------|
| V1 | Initial schema (users, alerts, alert_votes, user_subscriptions) |
| V2 | Seed data |
| V3 | Email verification (verification_tokens table) |
| V4 | PostGIS extension + geography column + GiST index on alerts |
| V5 | Credibility projection columns on alerts (upvote_count, downvote_count, confirmation_count, credibility_score) |

Next migration must be **V6**.

---

## Workflows (PowerShell — Windows is the dev OS)

```powershell
.\mvnw.cmd test                              # full suite (Docker required for PostGIS Testcontainers tests)
.\mvnw.cmd "-Dtest=AlertVoteServiceTest" test  # single test (quotes required in PowerShell)
.\mvnw.cmd clean package                     # builds jar + runs JaCoCo (fails <60% LINE BUNDLE coverage)
.\mvnw.cmd spring-boot:run                   # run app; needs Postgres on 127.0.0.1:5432 (or set SPRING_DATASOURCE_URL)
docker compose up -d postgres                # dev DB on host port 5433 (note: not 5432)
docker compose up --build                    # full stack on :8080
```

- Use `127.0.0.1`, not `localhost` (Windows IPv6 quirk — applied throughout).
- Coverage report: `target/site/jacoco/index.html`. JaCoCo excludes `**/config/**` and `*Request*`/`*Response*` DTOs.
- Checkstyle (`config/checkstyle/checkstyle.xml`) is advisory (`failOnViolation=false`).

---

## Testing patterns

- **Service tests**: plain JUnit 5 + Mockito on the `@UseCase` class, mocking out-ports.
- **Controller tests**: `@WebMvcTest` with security filters disabled.
- **Integration (H2)**: `@SpringBootTest` + H2 for auth/alert REST flows (no spatial queries).
- **Spatial repository**: `@DataJpaTest` + Testcontainers PostGIS. Docker required.
- **WebSocket**: not yet tested — known gap.

---

## Known gaps / next tasks

| Area | Status | Notes |
|------|--------|-------|
| `POST /api/v1/alerts` trusts `reportedBy` from body | ❌ Bug | Must read from JWT principal — security vulnerability |
| `PATCH /api/v1/alerts/{id}/resolve` endpoint | ❌ Missing | `Alert.resolve()` and `AlertStatus.RESOLVED` exist; no controller calls them |
| `Role.ADMIN` enforcement | ❌ Missing | Seeded in DB; no `@PreAuthorize` guards exist anywhere |
| `NotificationService` tests | ❌ Missing | Untested — use Mockito service test pattern |
| `AlertWebSocketHandler` tests | ❌ Missing | Untested |
| `GlobalExceptionHandler` tests | ❌ Missing | Untested |
| `GetNotificationUseCase` wiring | ❌ Missing | Interface exists; no REST controller exposes it |
| Optimistic UI vote endpoint | 🔜 Next | Backend votes API exists (`POST /api/v1/alerts/{id}/votes`); FE needs rollback support |
