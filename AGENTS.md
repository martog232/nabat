# AGENTS.md — Nabat backend

Spring Boot 3.4 / Java 21 real-time safety-alert service. Hexagonal (ports & adapters) layout under `org.example.nabat`. Read `README.md` for product context; this file is the agent-facing cheat sheet.

---

## Architecture rules (non-obvious)

Layers under `src/main/java/org/example/nabat/`:

- `domain/model` — pure Java **records** + value-object IDs (`AlertId`, `UserId`, …) and enums. No Spring/JPA/Lombok annotations here. Domain logic lives on records (e.g. `Alert.create(...)`, `Alert.resolve()`).
- `application/port/in` — use-case interfaces (one method, plus a nested `Command` record when needed, e.g. `CreateAlertUseCase.CreateAlertCommand`).
- `application/port/out` — driven ports (`AlertRepository`, `AlertNotificationPort`, `FileStoragePort`, `TokenProvider`, …). Implemented in `adapter/out/**`.
- `application/service` — use-case implementations. **Always annotate with `@UseCase`** (custom stereotype in `application/UseCase.java`). Don't use `@Service` and don't register them in `@Configuration` classes; `@UseCase` is itself a `@Component`.
- `adapter/in/rest` — `@RestController` + request/response DTO records under the same package; cross-cutting errors handled by `GlobalExceptionHandler`.
- `adapter/in/security` — `SecurityConfig`, `JwtAuthenticationFilter`, `JwtTokenProvider`, `LoginAttemptTracker`.
- `adapter/in/websocket` — `AlertWebSocketHandler` + `JwtHandshakeInterceptor`. Browser clients **must** first call `POST /api/v1/ws/tickets` (authenticated REST) to obtain a short-lived one-time ticket, then open `ws://.../ws/alerts?ticket=<ticket>`. Non-browser clients may send `Authorization: Bearer <accessToken>` on the HTTP upgrade instead. The `JwtHandshakeInterceptor` handles both paths and stores the resolved `UUID` under `JwtHandshakeInterceptor.USER_ID_ATTR` in the session attributes. **Never trust `?userId=` from the client.**
- `adapter/out/persistence` — `*JpaEntity` (Lombok `@Getter` only — **no `@Setter`**, so state transitions must go through the domain record; protected no-arg ctor) + `*JpaRepository` (Spring Data) + `*RepositoryAdapter` (`@Component` implementing the out-port). Migrations in `src/main/resources/db/migration/`.
- `adapter/out/notification` — `RedisWsPublisher` (implements `WsClusterRelay`) + `RedisWsSubscriber` (drives `LocalWsDelivery`) for cross-instance WebSocket delivery. Both interfaces are declared in `adapter/in/websocket`, so the outbound adapter depends on the inbound one and never the reverse. Broadcasts use sentinel recipient `"*"`; every relayed frame carries an `origin` instance id so a publisher discards its own echo.
- `adapter/out/storage` — `FileSystemStorageAdapter` implements `FileStoragePort`. Uploads are validated by **magic bytes** (`ImageContentType`), stored under a generated UUID name with a canonical extension, and served as `Content-Disposition: attachment` with `nosniff` and a locked-down CSP. Never trust the client's filename or `Content-Type`.
- `adapter/out/token` — `RedisWebSocketTicketRepository` and `RedisRefreshTokenStore`. Both must be Redis-backed, not in-memory: nabat-app runs 2 replicas, so per-instance state breaks ticket redemption and refresh-token replay detection.

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

### Voting via Kafka microservice (`application/service/ExternalVoteService.java`)
- `ExternalVoteService` delegates to `ExternalVotingPort` (HTTP bridge to the `nabat-voting` Kafka-backed microservice).
- **The caller's own access token is forwarded** (via `RequestContextPort.callerAccessToken()`); nabat-voting derives the voter from its `userId` claim. Never send a voter id in the body — it is rejected — and never authenticate with a shared service token, which would attribute every vote to one identity.
- `vote`/`removeVote` **return the resulting tallies**. Do not follow a write with `getVoteStats()`: that endpoint reads an asynchronously-updated projection and will return the pre-write counts.
- **No `@Transactional` on these methods.** They begin with a network call; the local write is one short transaction inside `AlertRepository.applyVoteCounts`, which updates and re-reads atomically.
- Failure mapping: 409 → `VoteConflictException` (409), 404 → `AlertNotFoundException` (404), 401/403/5xx/timeout → `ExternalServiceUnavailableException` (**503**). Do not collapse these — the frontend silently ignores vote conflicts, so an outage reported as 409 disappears.

### Alert state machine (`domain/model`)
- `AlertStatus` enum: `ACTIVE`, `RESOLVED`.
- `Alert.resolve()` throws `IllegalStateException` ("Alert is already resolved") if already resolved — it is **not** idempotent. It is the **only** way to transition status; the JPA entity has no setters, so there is no back door.
- `Alert.credibilityScore` is owned by nabat-voting and carried through unchanged. Never derive it locally.

### Notification system (`application/service`)
- `NotificationService` — creates and persists `Notification` records; delivers them in real time via `AlertWebSocketHandler.sendNotificationToUser(...)` if the user is online, or marks them for later retrieval if offline.
- `NotificationMilestones` — defines the credibility-score thresholds that trigger milestone notifications (e.g. first confirmation, viral alert).
- `ExternalVoteService` calls `SendNotificationUseCase.sendVoteNotification` and `sendMilestoneNotification` after each vote.

### Subscription fan-out (`application/service`)
- `SubscriptionService` — manages `UserSubscription` records (user ↔ alert-type pairs).
- `CreateAlertService` calls `UserSubscriptionRepository.findUsersSubscribedToAlertType(type)` and fans out WebSocket pushes to all matching online users via `AlertWebSocketHandler.sendAlertToUser(...)`.

### Real-time alert updates (WebSocket broadcast)
- `AlertWebSocketHandler` sends three frame types, all wrapped in `WsFrame`:
  - `NEW_ALERT` — per-user fan-out after create (via `sendAlertToUser`)
  - `ALERT_UPDATED` — broadcast to all connected users after vote or resolve (via `broadcastAlertUpdate`)
  - `NOTIFICATION` — per-user notification delivery (via `sendNotificationToUser`)
- **Frames carry REST response DTOs** (`AlertResponse`, `NotificationResponse`), never domain records. Serialising a domain record puts wrapped ids (`{"id":{"value":…}}`) and `isRead` on the wire, which does not match what the REST API returns for the same object.
- Sessions are held as `userId -> (sessionId -> session)`: a user may have several tabs open, and removal is by session id. Each session is wrapped in `ConcurrentWebSocketSessionDecorator` because frames are written from both request threads and the Redis listener thread.
- Cross-instance delivery via `WsClusterRelay`:
  - Per-user messages are addressed to the user's UUID.
  - Broadcasts use sentinel recipient `"*"`; the subscriber calls `deliverToAll()`.
  - Every relayed frame is stamped with the sending instance's id, and subscribers drop frames bearing their own — Redis echoes a publication back to the publisher, which would otherwise deliver each broadcast twice locally.

### Photo upload (two-step)
- `POST /api/v1/uploads` accepts `multipart/form-data`, returns `{ "url": "/api/v1/uploads/<uuid>.<ext>" }`.
- `GET /api/v1/uploads/{filename}` serves the stored file.
- `FileStoragePort` / `FileSystemStorageAdapter` saves to `nabat.storage.upload-dir` (default `./uploads`).
- `CreateAlertRequest` / `CreateAlertCommand` / `Alert` include optional `photoUrl` field.
- `V8__add_photo_url_to_alerts.sql` adds `photo_url VARCHAR(500)` to the `alerts` table.

---

## Conventions

- **New use case** = new in-port interface in `port/in` + new `@UseCase` service in `application/service`. Wire only via constructor injection of out-ports.
- **New persistence type** = entity + Spring Data repo + adapter implementing the out-port. Adapters are `@Component`, not `@Repository`.
- **REST DTOs** are records co-located with controllers. Validate request bodies with `@Valid` and request *parameters* with `@Validated` on the class plus constraints on the arguments.
- **Exceptions → HTTP** (see `GlobalExceptionHandler`): `IllegalArgumentException` → 400, `IllegalStateException` → 409, `AlertNotFoundException`/`UserNotFoundException`/`NotificationNotFoundException` → 404, `AuthenticationFailedException` → 401, `NotAuthorizedException` → 403, `VoteConflictException` → 409, `EmailAlreadyRegisteredException` → 409, `UnsupportedFileTypeException` → 415, `ExternalServiceUnavailableException` → 503.
- **Client-facing messages are curated constants, not `ex.getMessage()`.** Every response carries a stable `code` — the frontend branches on that, never on the prose. Validation responses are the exception: their per-field messages are written for the client.
- **Never throw Spring Security exceptions from domain or application layers** — use the domain exceptions above. This is enforced by `ArchitectureTest`.
- All HTTP routes are under `/api/v1`. `/api/v1/auth/**` is open; all other routes require `Authorization: Bearer <accessToken>`. Anything unmatched is `denyAll()` — add routes explicitly rather than relying on a permissive fallthrough. JWT filter sets authorities as `ROLE_<role>`.
- **`POST /api/v1/alerts`** must extract `reportedBy` from the authenticated principal, **not** from the request body.
- **`PATCH /api/v1/users/me/preferences`** updates the authenticated user's `notificationRadiusKm` (allow-list in `NotificationRadius`, mirroring the DB CHECK constraint) and optionally refreshes `lastKnownLat`/`lastKnownLng`.
- Config is env-var driven; **no Spring profiles**. Defaults in `application.properties`.
- **`JWT_SECRET` has no default and the app refuses to start without it.** It must be ≥ 32 chars, have ≥ 16 distinct characters, and not look like a placeholder. nabat-app and nabat-voting must share the same value. Do not reintroduce a fallback: a committed default is a publicly-known signing key.
- Access and refresh tokens carry a `tv` (token version) claim checked against `users.token_version`, so a password reset invalidates sessions already in flight. Refresh tokens are single-use, tracked by `jti` in Redis.
- CORS origins: `nabat.cors.allowed-origins` (comma-separated), resolved once by `AllowedOrigins` and shared by the HTTP and WebSocket configs. `*` is refused because credentials are enabled.

---

## Flyway migration history

| Version | Description |
|---------|-------------|
| V1 | Initial schema (users, alerts, alert_votes, user_subscriptions) |
| V2 | Seed data |
| V3 | Email verification (verification_tokens table) |
| V4 | PostGIS extension + geography column + GiST index on alerts |
| V5 | Credibility projection columns on alerts |
| V6 | User notification radius + last-known location columns on users |
| V7 | Remove internal votes table (migrated to voting microservice) |
| V8 | Add photo_url column to alerts |
| V9 | `users.token_version` — session invalidation on credential change |
| V10 | `alerts.version` — optimistic locking |

---

## Workflows (PowerShell — Windows is the dev OS)

```powershell
$env:JAVA_HOME="C:\Program Files\Java\jdk21.0.11_10"  # see the JDK note below
.\mvnw.cmd test                              # full suite (Docker required for PostGIS Testcontainers tests)
.\mvnw.cmd "-Dtest=AlertVoteControllerTest" test  # single test (quotes required in PowerShell)
.\mvnw.cmd clean package                     # builds jar + runs JaCoCo (fails <60% LINE BUNDLE coverage)
.\mvnw.cmd spring-boot:run                   # run app; needs Postgres on 127.0.0.1:5432 (or set SPRING_DATASOURCE_URL)
docker compose up -d postgres                # dev DB on host port 5433 (note: not 5432)
docker compose up --build                    # full stack on :8080
```

- **The build requires JDK 21.** Lombok 1.18.34 (pinned in `pom.xml`) cannot parse newer
  JDKs and fails with `ExceptionInInitializerError: com.sun.tools.javac.code.TypeTag ::
  UNKNOWN`. If `java -version` reports 22+, set `JAVA_HOME` to a 21 install for the build
  or upgrade Lombok. `<java.version>` is 21, so a 21 toolchain is the intended target.
- Compose requires `JWT_SECRET` to be set (see `.env.example`); it deliberately has no
  fallback.
- Use `127.0.0.1`, not `localhost` (Windows IPv6 quirk — applied throughout).
- Coverage report: `target/site/jacoco/index.html`. JaCoCo excludes `**/config/**` and `*Request*`/`*Response*` DTOs.
- Checkstyle (`config/checkstyle/checkstyle.xml`) is advisory (`failOnViolation=false`).

---

## Testing patterns

- **Service tests**: plain JUnit 5 + Mockito on the `@UseCase` class, mocking out-ports.
- **Controller tests**: `@WebMvcTest` with security filters disabled.
- **Integration**: `@SpringBootTest` + Testcontainers PostGIS for auth/alert REST flows.
- **Spatial repository**: `@DataJpaTest` + Testcontainers PostGIS. Docker required.
- **Architecture**: `ArchitectureTest` scans imports to enforce the layering rules above
  — domain stays framework-free, the application layer does not reach into adapters,
  inbound adapters do not depend on outbound ones, controllers use in-ports. It replaced
  a Modulith test that asserted the absence of a module name that could never exist, and
  therefore passed while four real violations accumulated.
- Build domain fixtures from `testsupport/Fixtures` and `User.toBuilder()` rather than
  calling record canonical constructors positionally, so adding a component does not
  break every test that builds one.

---

## Known gaps / next tasks

| Area | Status | Notes |
|------|--------|-------|
| `Role.ADMIN` enforcement | 🟡 Partial | `@PreAuthorize("hasRole('ADMIN')")` on `GET /api/v1/alerts` and on nabat-voting's projection rebuild. No other admin-only endpoints exist yet. |
| Notification REST API | ✅ Done | `NotificationController` exposes the 5 `GetNotificationUseCase` methods. There is deliberately no create endpoint. |
| Photo upload storage (multi-instance) | 🟡 Local FS only | `FileSystemStorageAdapter` uses local disk, so with `replicas: 2` a photo written by one pod is unreadable from the other unless the volume is RWX. Needs an S3/MinIO adapter. |
| Orphaned uploads | 🟡 Known | A photo uploaded for an alert whose creation then fails is never referenced or reclaimed. The frontend reuses the URL on retry; there is no server-side sweeper. |
| Kafka dual write | 🟡 Known | The vote commit and the `vote.cast` publish are not atomic. Failures are logged and the projection is rebuildable; a transactional outbox is the proper fix. |
| Shared JWT secret | 🟡 Known | Symmetric HS256 means nabat-voting could also *mint* tokens nabat-app trusts. RS256 with a published public key would let it verify without signing power. |
| Spring Boot version drift | 🟡 Known | nabat-app 3.4.1 (past OSS support, Jackson 2) vs nabat-voting 4.0.6 (Jackson 3). Worth aligning. |
| Boot 3.4.1 / Lombok / JDK | 🟡 Known | The build needs JDK 21; Lombok 1.18.34 fails on newer JDKs. |
