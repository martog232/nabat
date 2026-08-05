# AGENTS.md — Nabat backend

Spring Boot 3.4 / Java 21 real-time safety-alert service. **Spring Modulith modules, hexagonal inside each**, under `org.example.nabat`. Read `README.md` for product context, `TARGET_ARCHITECTURE.md` for where this is heading; this file is the agent-facing cheat sheet for what is here now.

---

## Architecture rules (non-obvious)

### Modules

Each direct sub-package of `org.example.nabat` is a Spring Modulith module. `ModularityTest` runs `ApplicationModules.verify()`, so a cross-module reference that is not part of the target module's declared API **fails the build**, as does any dependency cycle.

| Module | Owns |
|---|---|
| `identity` | users, verification tokens, JWT issuing and verification, login-attempt tracking, the security filter chain |
| `incident` | alerts: the write model, the state machine, nearby queries, the creation fan-out |
| `voting` | the HTTP bridge to nabat-voting and the local vote-count projection |
| `notification` | notification records, delivery, milestone policy |
| `subscription` | user ↔ alert-type subscriptions and the alert audience they imply |
| `realtime` | WebSocket transport: sessions, tickets, cross-instance relay |
| `media` | photo upload and retrieval |
| `shared` | value types every module needs (`Location`, `NotAuthorizedException`, `@UseCase`) plus infrastructure beans. **Depends on nothing.** |
| `platform` | app-wide composition: `GlobalExceptionHandler`, request logging, use-case metrics, async enablement. **Depended on by nothing.** |

`shared` is the only sink and `platform` the only source; everything else forms a DAG between them.

### Layers, inside every module

- `<module>/domain` — pure Java **records**, value-object IDs and enums. No Spring/JPA/Lombok/Jackson. Domain logic lives on the records (`Alert.create(...)`, `Alert.resolve()`). Domain events live here too (`AlertCreated`).
- `<module>/application/port/in` — use-case interfaces, with a nested `Command` record where needed (`CreateAlertUseCase.CreateAlertCommand`).
- `<module>/application/port/out` — driven ports, implemented under `<module>/adapter/out/**`. A port may be implemented by **another** module when that module owns the data: `subscription` implements `incident`'s `AlertAudiencePort`. That is the intended direction — the module that needs something declares the port, the module that has it implements it.
- `<module>/application` — use-case implementations. **Always annotate with `@UseCase`** (`shared/UseCase.java`). Not `@Service`, and never registered as explicit `@Bean` methods; `@UseCase` is itself a `@Component`. Event handlers here are plain `@Component`.
- `<module>/adapter/in/rest` — `@RestController` plus request/response DTO records in the same package.
- `<module>/adapter/out/persistence` — `*JpaEntity` (Lombok `@Getter` only — **no `@Setter`**, so state transitions go through the domain record; protected no-arg ctor) + `*JpaRepository` + `*RepositoryAdapter` (`@Component` implementing the out-port). Migrations in `src/main/resources/db/migration/`.

### Exposing a module's API

A module's own root package is public; nested packages are internal unless a `package-info.java` marks them `@NamedInterface`. By convention each module exposes exactly `domain`, `application/port/in` and `application/port/out`. `realtime` additionally exposes `realtime/spi`.

**Adapters are never exposed.** If another module appears to need one, the dependency is pointing the wrong way — see how `realtime` came to depend on `incident`'s REST DTOs and had to be inverted (`WsBroadcaster`).

Persistence is PostgreSQL with **Flyway**. `spring.jpa.hibernate.ddl-auto=validate` — never let Hibernate auto-create or update the schema.

---

## Implemented features (current state)

### WebSocket authentication (`realtime/adapter/in/websocket`)
- `JwtHandshakeInterceptor` validates on the HTTP upgrade. Two accepted auth paths:
  1. `Authorization: Bearer <accessToken>` header (non-browser clients).
  2. `?ticket=<one-time-ticket>` query param (browser clients). Tickets are issued by `POST /api/v1/ws/tickets` and redeemed via `RedeemWebSocketTicketUseCase` (backed by `WebSocketTicketService`). A ticket is single-use and short-lived.
- `AlertWebSocketHandler` reads `userId` exclusively from `session.getAttributes()` — never from query params.

### Spatial queries (`incident`/`subscription`/`identity` persistence + migrations)
- `V4__postgis_spatial_indexes.sql` enables the `postgis` extension and adds a `GEOGRAPHY(Point, 4326)` column + GiST index on `alerts`. Nearby-alert queries use `ST_DWithin` instead of Haversine.
- Tests that exercise spatial queries use **Testcontainers** with a PostGIS image (`@DataJpaTest`). Docker is required for those tests.

### Voting via Kafka microservice (`voting/application/ExternalVoteService.java`)
- `ExternalVoteService` delegates to `ExternalVotingPort` (HTTP bridge to the `nabat-voting` Kafka-backed microservice).
- **The caller's own access token is forwarded** (via `RequestContextPort.callerAccessToken()`); nabat-voting derives the voter from its `userId` claim. Never send a voter id in the body — it is rejected — and never authenticate with a shared service token, which would attribute every vote to one identity.
- `vote`/`removeVote` **return the resulting tallies**. Do not follow a write with `getVoteStats()`: that endpoint reads an asynchronously-updated projection and will return the pre-write counts.
- **No `@Transactional` on these methods.** They begin with a network call; the local write is one short transaction inside `AlertRepository.applyVoteCounts`, which updates and re-reads atomically.
- Failure mapping: 409 → `VoteConflictException` (409), 404 → `AlertNotFoundException` (404), 401/403/5xx/timeout → `ExternalServiceUnavailableException` (**503**). Do not collapse these — the frontend silently ignores vote conflicts, so an outage reported as 409 disappears.

### Alert state machine (`incident/domain`)
- `AlertStatus` enum: `ACTIVE`, `RESOLVED`.
- `Alert.resolve()` throws `IllegalStateException` ("Alert is already resolved") if already resolved — it is **not** idempotent. It is the **only** way to transition status; the JPA entity has no setters, so there is no back door.
- `Alert.credibilityScore` is owned by nabat-voting and carried through unchanged. Never derive it locally.

### Notification system (`notification`)
- `NotificationService` — creates and persists `Notification` records; delivers them in real time via `NotificationSender` if the user is reachable, and relies on the persisted row otherwise.
- `NotificationMilestones` (in `notification/domain`) — the confirmation thresholds that trigger milestone notifications.
- `ExternalVoteService` calls `SendNotificationUseCase.sendVoteNotification` and `sendMilestoneNotification` after each vote. It passes a `NotificationType`, **not** a `VoteType`: this module must not know what a vote is, or it would depend on the module already calling it.

### Subscription fan-out (`incident` + `subscription`)
- `SubscriptionService` manages `UserSubscription` records (user ↔ alert-type pairs).
- `CreateAlertService` saves the alert and publishes `AlertCreated`. It resolves no audience and pushes nothing.
- `NewAlertFanout` handles that event with **`@ApplicationModuleListener`** — after commit, async, in a new transaction. It asks `AlertAudiencePort` for recipients, drops the reporter, and broadcasts.
- `AlertAudienceAdapter` (in `subscription`) implements that port, merging subscribers-by-type with users whose own notification radius covers the incident.
- **Do not move the fan-out back inside `createAlert`.** It was there, in the transaction: it held a pooled connection across socket writes, and because the push preceded the commit, a rollback left clients displaying an alert that did not exist.
- The listener is **not durable** — a crash between commit and delivery loses the push. Acceptable only because the frontend re-fetches with `GET /alerts/nearby?since` after a dropped socket. Do not extend this pattern to anything the client cannot re-derive; that needs Modulith's Event Publication Registry.

### Real-time alert updates (WebSocket broadcast)
- Three frame types, all wrapped in `WsFrame`:
  - `NEW_ALERT` — per-user fan-out after create
  - `ALERT_UPDATED` — broadcast to all connected users after a vote or a resolve
  - `NOTIFICATION` — per-user notification delivery
- **The producing module builds the frame**; `realtime` only routes and serialises it. `WsBroadcaster` (in `realtime/spi`) takes a `WsFrame`, never a domain record — the handler used to accept `Alert` and `Notification` and convert them itself, which made `realtime` depend on the two modules that depend on it.
- **Frames carry REST response DTOs** (`AlertResponse`, `NotificationResponse`), never domain records. Serialising a domain record puts wrapped ids (`{"id":{"value":…}}`) and `isRead` on the wire, which does not match what REST returns for the same object.
- Sessions are held as `userId -> (sessionId -> session)`: a user may have several tabs open, and removal is by session id. Each session is wrapped in `ConcurrentWebSocketSessionDecorator` because frames are written from both request threads and the Redis listener thread.
- Cross-instance delivery via `WsClusterRelay`:
  - Per-user messages are addressed to the user's UUID.
  - Broadcasts use sentinel recipient `"*"`; the subscriber calls `deliverToAll()`.
  - Every relayed frame is stamped with the sending instance's id, and subscribers drop frames bearing their own — Redis echoes a publication back to the publisher, which would otherwise deliver each broadcast twice locally.

### Photo upload (two-step, `media`)
- `POST /api/v1/uploads` accepts `multipart/form-data`, returns `{ "url": "/api/v1/uploads/<uuid>.<ext>" }`.
- `GET /api/v1/uploads/{filename}` serves the stored file. **Authenticated**, which is why these URLs cannot be CDN-cached today.
- `UploadController` → `StorePhotoUseCase` / `LoadPhotoUseCase` → `PhotoStorageService` → `FileStoragePort`. The controller was wired straight to `FileStoragePort` and was the only controller in the codebase naming an outbound port; `ArchitectureTest` now forbids that.
- `FileSystemStorageAdapter` saves to `nabat.storage.upload-dir` (default `./uploads`). Uploads are validated by **magic bytes** (`ImageContentType`), stored under a generated UUID name with a canonical extension, and served as `Content-Disposition: attachment` with `nosniff` and a locked-down CSP. Never trust the client's filename or `Content-Type`.
- `CreateAlertRequest` / `CreateAlertCommand` / `Alert` include optional `photoUrl` field.
- `V8__add_photo_url_to_alerts.sql` adds `photo_url VARCHAR(500)` to the `alerts` table.

---

## Conventions

- **New use case** = new in-port interface in `<module>/application/port/in` + new `@UseCase` service in `<module>/application`. Wire only via constructor injection of ports.
- **New module** = new direct sub-package of `org.example.nabat`, with `package-info.java` `@NamedInterface` markers on the packages it exposes. Run `ModularityTest` before anything else; it tells you immediately whether the boundary you drew is real.
- **New persistence type** = entity + Spring Data repo + adapter implementing the out-port. Adapters are `@Component`, not `@Repository`.
- **REST DTOs** are records co-located with controllers. Validate request bodies with `@Valid` and request *parameters* with `@Validated` on the class plus constraints on the arguments.
- **Exceptions → HTTP** (see `GlobalExceptionHandler`): `IllegalArgumentException` → 400, `IllegalStateException` → 409, `AlertNotFoundException`/`UserNotFoundException`/`NotificationNotFoundException` → 404, `AuthenticationFailedException` → 401, `NotAuthorizedException` → 403, `VoteConflictException` → 409, `EmailAlreadyRegisteredException` → 409, `UnsupportedFileTypeException` → 415, `ExternalServiceUnavailableException` → 503.
- **Client-facing messages are curated constants, not `ex.getMessage()`.** Every response carries a stable `code` — the frontend branches on that, never on the prose. Validation responses are the exception: their per-field messages are written for the client.
- **Never throw Spring Security exceptions from domain or application layers** — use the domain exceptions above. This is enforced by `ArchitectureTest`.
- All HTTP routes are under `/api/v1`. The public set is **enumerated by method and path** in `SecurityConfig` — the six `POST /api/v1/auth/*` endpoints — and everything else requires `Authorization: Bearer <accessToken>`. Do not restore a `/api/v1/auth/**` wildcard: it also matched `GET /api/v1/auth/me`, which returns the caller's own profile, so an anonymous request reached `UserResponse.from(null)` and produced a 500 where a 401 was intended. Anything unmatched is `denyAll()`. The JWT filter sets authorities as `ROLE_<role>`.
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

Tests mirror the module layout: a test lives in the same package as its subject, which is
what lets it use package-private members instead of widening production visibility.

- **Service tests**: plain JUnit 5 + Mockito on the `@UseCase` class, mocking ports.
- **Controller tests**: `@WebMvcTest` with `addFilters = false`. Note that `@WebMvcTest`
  still instantiates `JwtAuthenticationFilter` (a `@Component` that is a `Filter`), so its
  collaborators need `@MockitoBean` entries even when filters are off.
- **Security rules**: `AuthEndpointSecurityTest` — `@WebMvcTest` with filters *enabled* and
  the real `SecurityConfig`, no database. Which routes are public is a rule worth checking
  on every run, not only where Docker exists.
- **Integration**: `@SpringBootTest` + Testcontainers PostGIS for auth/alert REST flows.
- **Spatial repository**: `@DataJpaTest` + Testcontainers PostGIS. Docker required.
- **Module boundaries**: `ModularityTest` runs `ApplicationModules.verify()`. Cycles and
  references to non-exposed types fail here.
- **Layering**: `ArchitectureTest` uses **ArchUnit** over `..domain..`, `..application..`
  and `..adapter.in..` package patterns. It replaced an import-scanning version whose four
  hard-coded directory paths stopped existing when packages became feature-first.
- **Wiring that would otherwise fail silently**: `NewAlertFanoutWiringTest` builds a
  minimal context with a no-op transaction manager and asserts the fan-out actually runs,
  after commit, off the publishing thread. A handler unit test cannot catch a missing
  `@EnableAsync` or an unregistered listener; both were verified by removing them and
  watching this test fail.
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
| Alert fan-out durability | 🟡 Known | `NewAlertFanout` is an async after-commit listener, so a crash between commit and delivery loses the push. Survivable only because the frontend catches up via `?since` on reconnect. The fix is Modulith's Event Publication Registry (`spring-modulith-events-jpa` + a table), which turns the same annotation into a transactional outbox. |
| Orphaned uploads | 🟡 Known | A photo uploaded for an alert whose creation then fails is never referenced or reclaimed. The frontend reuses the URL on retry; there is no server-side sweeper. |
| Kafka dual write | 🟡 Known | The vote commit and the `vote.cast` publish are not atomic. Failures are logged and the projection is rebuildable; a transactional outbox is the proper fix. |
| Shared JWT secret | 🟡 Known | Symmetric HS256 means nabat-voting could also *mint* tokens nabat-app trusts. RS256 with a published public key would let it verify without signing power. |
| Spring Boot version drift | 🟡 Known | nabat-app 3.4.1 (past OSS support, Jackson 2) vs nabat-voting 4.0.6 (Jackson 3). Worth aligning. |
| Boot 3.4.1 / Lombok / JDK | 🟡 Known | The build needs JDK 21; Lombok 1.18.34 fails on newer JDKs. |
