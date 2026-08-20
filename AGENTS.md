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
- **Both paths end in `AuthenticateSessionUseCase` (`identity`), which is also the only thing `JwtAuthenticationFilter` consults.** Verifying a signature is not the same as accepting a session: the account may be disabled or deleted, and a password reset bumps `tv` so tokens minted before it are stale inside their expiry window. Those checks lived only in the HTTP filter, so the handshake — the one other place that authenticates a bearer token — accepted revoked tokens, and a socket outlives its token by hours. Do not re-add a local `TokenProvider` call here.
- A **redeemed ticket carries no token version**, so it proves only that its holder was authenticated at issue time. `resolveActiveUser` re-checks the account at redemption, because the ticket's whole lifetime sits after that point.
- `AlertWebSocketHandler` reads `userId` exclusively from `session.getAttributes()` — never from query params.
- **Revocation still does not close sockets that are already open.** The handshake is the only gate; nothing re-authenticates a live session, so a password reset takes effect on the next connect. Closing live sessions needs a revocation event routed through `WsClusterRelay` — every replica holds its own sessions.

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
- The listener **is durable**, via Modulith's Event Publication Registry (`spring-modulith-events-jpa`, table `event_publication`, V11). Publishing writes one row per (event, listener) in the publishing transaction; it is completed when the listener returns. Outstanding rows are replayed on startup — `spring.modulith.events.republish-outstanding-events-on-restart=true`. Delivery is therefore **at-least-once, not exactly-once**: a crash after the socket write but before completion replays the push, which is safe only because the frontend upserts by alert id.
- The registry needs an `EventSerializer`; `spring-modulith-events-jpa` ships none, so `spring-modulith-events-jackson` is a required companion dependency. Without it the context fails at startup.
- Anything added to an event payload must survive a **Jackson round-trip**, because replay deserialises the stored JSON. A payload that serialises but does not deserialise turns every outstanding row into a permanent failure, discovered only after a crash.

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
- **Two storage adapters behind one port**, chosen by `nabat.storage.type` in `StorageConfig`: `filesystem` (default) and `s3`. Neither carries a stereotype annotation — the config class registers exactly one, so a second `FileStoragePort` cannot appear by accident. `pathStyleAccessEnabled` is **required**: the SDK's default virtual-host addressing (`bucket.host/key`) needs wildcard DNS that MinIO does not have. The S3 adapter buffers the whole upload in memory (bounded by `max-file-size-bytes`) because `PutObject` needs the length up front and the magic-byte check needs the first bytes first.
- Bucket creation is **not** the application's job — a service that creates its own bucket creates one from a typo just as happily. Compose uses an `mc` init container, the chart a post-install Job.
- `FileSystemStorageAdapter` saves to `nabat.storage.upload-dir` (default `./uploads`). Uploads are validated by **magic bytes** (`ImageContentType`), stored under a generated UUID name with a canonical extension, and served as `Content-Disposition: attachment` with `nosniff` and a locked-down CSP. Never trust the client's filename or `Content-Type`.
- `CreateAlertRequest` / `CreateAlertCommand` / `Alert` include optional `photoUrl` field.
- **Orphan reclamation** (`OrphanedPhotoReclaimService`): the two-step upload means a photo whose alert is never submitted has nothing referencing it. The sweep deletes from the *absence* of a reference, which makes two rules non-negotiable. **A failure to determine references must propagate, never degrade to an empty set** — the caller deletes everything not in that set, so "the database is down" would otherwise read as "nothing is referenced" and erase the volume. And **only files older than the grace period are candidates**, because a fresh upload is usually sitting in an unsubmitted form.
- `media` declares `PhotoReferencePort`; `incident` implements it (`AlertPhotoReferenceAdapter`) because it owns `alerts.photo_url`. Same direction as `AlertAudiencePort`. The SQL matches the **last path segment**, not a `LIKE '%' || name` suffix — the latter reports `graph.jpg` as referenced whenever some alert points at `photograph.jpg`, so orphans are never reclaimed.
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
- **`GET /api/v1/alerts/nearby` returns an envelope, not a bare array**: `{ alerts, count, limit, truncated }`. It is capped (`limit`, default 100, max 500) and accepts optional `type`/`severity` filters. The cap and the filters are applied **in SQL** — trimming in Java would still make the database materialise every row in the radius. Not Spring Data `Page`: nothing pages through a map, and its internals would become a public contract. A client seeing `truncated` should narrow the radius or filter, not fetch a next page.
- **Anything that narrows the nearby result set must be in `NearbyAlertsQuery.cacheKey()`.** The response is cached for 15s on a ~110 m grid, so a filter missing from the key means a request for `type=FIRE` can be served an unfiltered neighbour's cached answer — wrong alerts on a safety map, visible only under mixed concurrent traffic.
- **`PATCH /api/v1/users/me/preferences`** updates the authenticated user's `notificationRadiusKm` (allow-list in `NotificationRadius`, mirroring the DB CHECK constraint) and optionally refreshes `lastKnownLat`/`lastKnownLng`.
- **Liveness and readiness are separate groups and must stay separate.** `/actuator/health/liveness` carries no external dependency — a restart does not repair Postgres, and both probes used to point at the aggregate, so an outage got the pod restarted in a loop. `/actuator/health/readiness` adds `db`. **Redis is deliberately not in readiness**: every replica shares one, so including it turns a partial degradation into every pod leaving the load balancer at once. Both paths are enumerated in `SecurityConfig` (the fallback is `denyAll()`, and probes are unauthenticated). Pinned by `HealthProbesIntegrationTest`.
- **`src/test/resources/application.properties` replaces the main one, it does not merge.** Anything a test needs to see must be repeated there. The health-group settings are duplicated for this reason, and a change in only one file leaves the test green while production drifts.
- **Logs are JSON in deployments, plain text locally.** `logging.structured.format.console=${NABAT_LOG_FORMAT:}` — empty by default, `ecs` in `docker-compose.yml` and the Helm chart. Boot 3.4 has the formatter built in; no `logstash-logback-encoder`. Setting it makes `logging.pattern.console` inert. Two things worth knowing before editing it: ECS keys are **flat with dots** (`service.name`, not a nested object), and tracing arrives as **`traceId`/`spanId`, not the ECS-canonical `trace.id`** — MDC is emitted as dynamic pairs, so `logging.structured.json.rename.*` silently does not reach it. Query `traceId`. Pinned by `StructuredLoggingIntegrationTest`.
- **The observability stack is part of both environments, and its addresses are stated, not inferred.** Compose runs Prometheus, Grafana, Loki, Promtail and Zipkin; the chart renders the same five. `ZIPKIN_ENDPOINT` is set explicitly in each — compose to the `zipkin` service name, the chart to `{{ fullname }}-zipkin` — because the application default is the literal host `nabat-zipkin`, which resolves only while the release happens to be named `nabat`. A tracing backend that receives nothing looks exactly like a system with no traffic, so do not go back to relying on that default. Grafana's compose datasources address services by **compose service name**, not `container_name`.
- **Promtail is configured twice and cannot be configured once.** `promtail-config.yml` (compose) uses Docker service discovery, which hands over bare log lines. The chart's ConfigMap tails files under `/var/log/pods` and therefore needs a `__path__` relabel — without it, discovery finds pods and ships nothing at all — plus a `cri: {}` stage to strip the kubelet's `<ts> stdout F ` wrapper before the application's JSON is parseable. Everything after that stage is identical in both: `json` extracting flat dotted ECS keys, `labels` promoting only `level` and `traceId` (labels are indexed; high cardinality is what makes Loki slow), and `timestamp` from the application's own field.
- **Tracing context crosses `@Async` only because `AsyncConfig` declares a `TaskDecorator`.** Spring Boot registers none on its own; it applies one if a single `TaskDecorator` bean exists. What propagates is an **Observation**, not a bare span — only `ObservationThreadLocalAccessor` is ServiceLoader-registered, so `tracer.withSpan(...)` outside an Observation travels nowhere. Spring MVC wraps every request in one, so request-initiated work is covered.
- Config is env-var driven; **no Spring profiles**. Defaults in `application.properties`.
- **`JWT_SECRET` has no default and the app refuses to start without it.** It must be ≥ 32 chars, have ≥ 16 distinct characters, and not look like a placeholder. nabat-app and nabat-voting must share the same value. Do not reintroduce a fallback: a committed default is a publicly-known signing key.
- **Password rules live in `identity/domain/PasswordPolicy`, not in the DTOs.** ≥10 characters, at least one letter and one digit, and **at most 72 UTF-8 bytes** — BCrypt hashes only the first 72 bytes and discards the rest silently, so a longer passphrase would be truncated and any string sharing that prefix would unlock the account. The bound is bytes, not characters, because that is what BCrypt truncates (Cyrillic costs two bytes each). Applied through `@StrongPassword` on both `RegisterRequest` and `ResetPasswordRequest` — they previously each carried their own `@Size(min = 6)`, so reset was a way around any tightening of registration. Login does **not** apply the policy: existing accounts with older, weaker passwords must keep working.
- Access and refresh tokens carry a `tv` (token version) claim checked against `users.token_version`, so a password reset invalidates sessions already in flight. That check — with the exists and enabled checks — lives in `AuthenticateSessionUseCase` and nowhere else, so every entry point that accepts a token gets all three. Refresh tokens are single-use, tracked by `jti` in Redis.
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
| V11 | `event_publication` — Modulith Event Publication Registry (transactional outbox) |

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
- **Trace propagation across `@Async`**: `FanoutTracePropagationIntegrationTest` asserts
  the fan-out thread sees the publishing request's trace id, in the tracer *and* in the
  MDC — they can disagree, and it is the MDC that the log pattern reads. Verified by
  removing the `TaskDecorator` bean and watching it fail.
- **Outbox durability**: `AlertCreatedOutboxIntegrationTest` asserts against the
  `event_publication` table directly. A crash is simulated by making the delivery port
  throw, which leaves the same state a crash would: an outstanding row. It also proves the
  stored payload round-trips back into an equal `Alert`, since replay depends on that.
- **Docker is not optional, and its absence is silent.** `PostgresTestSupport` is
  `@Testcontainers(disabledWithoutDocker = true)`, so when Testcontainers cannot reach the
  daemon every integration and persistence test *skips* and the build still passes. Check
  `Skipped: 0` in the surefire summary, not just `BUILD SUCCESS`. `api.version` is pinned
  to 1.41 in the surefire config for this reason — see the comment in `pom.xml`.
- Build domain fixtures from `testsupport/Fixtures` and `User.toBuilder()` rather than
  calling record canonical constructors positionally, so adding a component does not
  break every test that builds one.

---

## Known gaps / next tasks

| Area | Status | Notes |
|------|--------|-------|
| `Role.ADMIN` enforcement | 🟡 Partial | `@PreAuthorize("hasRole('ADMIN')")` on `GET /api/v1/alerts` and on nabat-voting's projection rebuild. No other admin-only endpoints exist yet. |
| Notification REST API | ✅ Done | `NotificationController` exposes the 5 `GetNotificationUseCase` methods. There is deliberately no create endpoint. |
| Photo upload storage (multi-instance) | ✅ Done | `S3StorageAdapter` selected by `nabat.storage.type=s3`; MinIO in compose and in the chart. `filesystem` remains the default for single-process local runs. |
| Photo serving is not CDN-cacheable | 🟡 Known | `GET /api/v1/uploads/{filename}` still streams through the application behind JWT, so every byte crosses it. Presigned URLs are the fix and belong with the media-service split (phase 4), since they change the API contract. |
| Alert fan-out durability | ✅ Done | Event Publication Registry (V11). Outstanding publications replay on startup; delivery is at-least-once. |
| Orphaned uploads | ✅ Done | `OrphanedPhotoSweeper` → `ReclaimOrphanedPhotosUseCase`, hourly, behind `nabat.storage.orphan-sweep.enabled` (on in compose and Helm). Only files older than `nabat.storage.orphan-grace` (24h) are candidates. |
| Kafka dual write | 🟡 Known | The vote commit and the `vote.cast` publish are not atomic. Failures are logged and the projection is rebuildable; a transactional outbox is the proper fix. |
| Revocation of live WebSocket sockets | 🟡 Known | The handshake authenticates fully (`AuthenticateSessionUseCase`), but nothing re-checks a session already open, so a password reset or a disable only takes effect on the next connect. The fix is a revocation event relayed through `WsClusterRelay`, since each replica holds its own sessions. |
| Shared JWT secret | 🟡 Known | Symmetric HS256 means nabat-voting could also *mint* tokens nabat-app trusts. RS256 with a published public key would let it verify without signing power. |
| Spring Boot version drift | 🟡 Known | nabat-app 3.4.1 (past OSS support, Jackson 2) vs nabat-voting 4.0.6 (Jackson 3). Worth aligning. |
| Boot 3.4.1 / Lombok / JDK | 🟡 Known | The build needs JDK 21; Lombok 1.18.34 fails on newer JDKs. |
