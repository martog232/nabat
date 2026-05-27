# Nabat — Task Backlog

Concrete, actionable tasks derived from the current state of the codebase.
Each task has: a short rationale, the files to touch, and acceptance criteria
("Done when").

Legend: 🅿️ priority — **P0** ship-blockers / security, **P1** core features,
**P2** quality, **P3** nice-to-have.

---

## P1 — Core features still incomplete

> **Status (2026-04-30):** ✅ All P1 tasks (T-10 → T-16) implemented. 120 tests pass.

### T-10 🔔 Implement `SendNotificationUseCase` for vote events ✅ COMPLETED
- **Why:** Both `sendVoteNotification` and `sendMilestoneNotification` are
  stubs returning `null`; `AlertVoteService.vote` does not emit notifications.
- **Files:** `application/service/NotificationService.java`,
  `application/service/AlertVoteService.java`,
  `application/port/out/AlertRepository.java` (need `findById` for owner lookup),
  `adapter/out/persistence/AlertRepositoryAdapter.java`,
  `adapter/out/notification/SimpleNotificationSender.java`.
- **Done when:**
  - On every successful vote, `AlertVoteService` calls
    `SendNotificationUseCase.sendVoteNotification(...)` for the alert owner
    (skip self-votes).
  - Confirmation count crossing a milestone (10, 25, 50, 100, 250, 500, 1000)
    triggers `sendMilestoneNotification(...)`.
  - Notifications are persisted **and** delivered through `NotificationSender`
    (which today only logs — see T-11).
  - Unit tests cover: not-self vote → notification, self-vote → none,
    milestone boundary, no-double-notification on vote switch.

### T-11 📨 Wire `NotificationSender` to the WebSocket handler ✅ COMPLETED
- **Why:** `SimpleNotificationSender` only logs, so notifications never reach the client.
- **Files:** `adapter/out/notification/SimpleNotificationSender.java` (replace),
  `adapter/in/websocket/AlertWebSocketHandler.java` (add `sendNotificationToUser`).
- **Done when:**
  - A `WebSocketNotificationSender` adapter implements `NotificationSender`
    and pushes JSON `{ "type": "NOTIFICATION", "notification": {...} }`.
  - Falls back to log when the user has no active session.

### T-12 🌐 Add `NotificationController` ✅ COMPLETED
- **Why:** `GetNotificationUseCase` has a full implementation but no REST surface.
- **Files (new):** `adapter/in/rest/NotificationController.java`,
  `NotificationResponse.java`.
- **Endpoints:**
  - `GET    /api/v1/notifications`
  - `GET    /api/v1/notifications/unread`
  - `GET    /api/v1/notifications/unread/count`
  - `POST   /api/v1/notifications/{id}/read`
  - `POST   /api/v1/notifications/read-all`
- **Done when:** all 5 endpoints exist, are JWT-protected, and have at least
  one happy-path slice test each.

### T-13 📡 Implement user subscriptions end-to-end ✅ COMPLETED
- **Why:** `SubscribeToAlertsUseCase` is interface-only;
  `InMemoryUserSubscriptionRepository` always returns an empty list — so the
  WebSocket broadcast never fans out. The DB table `user_subscriptions` is
  already seeded but unused.
- **Files (new):** `application/service/SubscriptionService.java`,
  `adapter/out/persistence/UserSubscriptionJpaEntity.java`,
  `UserSubscriptionJpaRepository.java`,
  `UserSubscriptionRepositoryAdapter.java`,
  `adapter/in/rest/SubscriptionController.java`.
- **Files (delete):** `adapter/out/persistence/InMemoryUserSubscriptionRepository.java`.
- **Endpoints (proposed):**
  - `GET    /api/v1/subscriptions` — list mine
  - `POST   /api/v1/subscriptions` — `{ alertType, latitude, longitude, radiusKm }`
  - `DELETE /api/v1/subscriptions/{id}`
- **Done when:**
  - Creating an alert in the matching radius/type produces a WebSocket push
    to subscribed users (verified by integration test).
  - `findUsersSubscribedToAlertType` uses a real PostGIS-free Haversine
    filter (mirror the alert query).

### T-14 ✅ Alert lifecycle: resolve & fetch by id ✅ COMPLETED
- **Why:** `AlertStatus.RESOLVED`, `resolved_at` and `AlertNotFoundException`
  exist but are unreachable from the API.
- **Files:** `domain/model/Alert.java` (add `resolve()` factory),
  `adapter/out/persistence/AlertJpaEntity.java` (add `resolvedAt` column &
  Flyway migration if not yet present), new `ResolveAlertUseCase`,
  `AlertController` endpoints.
- **Endpoints:**
  - `GET   /api/v1/alerts/{id}` — 404 via `AlertNotFoundException`
  - `POST  /api/v1/alerts/{id}/resolve` — only the reporter or an ADMIN
- **Done when:** new endpoints exist with tests, including authorization checks.

### T-15 👮 Role-based authorization ✅ COMPLETED
- **Why:** `Role.ADMIN` is seeded but never enforced.
- **Files:** `SecurityConfig.java` (`@EnableMethodSecurity`),
  `JwtAuthenticationFilter.java` (set authorities from `User.role()`),
  `User.java` if needed.
- **Done when:**
  - `JwtAuthenticationFilter` sets a `SimpleGrantedAuthority("ROLE_" + role)`.
  - At least one admin-only endpoint uses `@PreAuthorize("hasRole('ADMIN')")`
    (e.g. `DELETE /api/v1/alerts/{id}` or list-all-alerts).
  - Unit tests assert 403 for non-admin.

### T-16 🔁 Decide vote idempotency semantics ✅ COMPLETED
- **Decision:** same-type re-vote → 200 OK, no-op (returns existing vote);
  switch-type → UPDATE preserving id, refreshes `createdAt`.
- **Why:** Re-voting the same `voteType` currently throws
  `IllegalStateException` → 409. Switching vote does delete + insert, which
  changes the vote id and `createdAt`.
- **Files:** `AlertVoteService.java`, `AlertVoteServiceTest.java`.
- **Done when:** product decision recorded in this task; behavior implemented;
  tests updated accordingly. Suggestion: same-type → 200 idempotent;
  switch-type → UPDATE (preserve id, refresh `createdAt`).

---

## P2 — Quality, tests, observability

### T-20 🧪 Tests for `NotificationService` ✅ COMPLETED
- `application/service/NotificationServiceTest.java` covers:
  `markAsRead` happy path, already-read short-circuit, wrong-user 400,
  not-found 400, `markAllAsRead` delegation, list/unread/count delegation,
  plus vote/milestone notification persistence + delivery behavior.

### T-21 🧪 Tests for `JwtAuthenticationFilter` ✅ COMPLETED
- `adapter/in/security/JwtAuthenticationFilterTest.java` covers: no header,
  invalid bearer, valid access token (principal set + `ROLE_<role>`),
  disabled/missing user, refresh token rejected for API auth, malformed `userId`
  claim clearing security context, and lookup by stable `userId`.
- Integration check in `AuthControllerIntegrationTest` verifies refresh token
  used against protected endpoint (`/api/v1/auth/me`) returns 401.

### T-22 🧪 Tests for `GlobalExceptionHandler` ✅ COMPLETED
- `adapter/in/rest/GlobalExceptionHandlerTest.java` has one test per branch:
  `BadCredentials`, `IllegalArgument`, `IllegalState`, `AlertNotFound`,
  `MethodArgumentNotValid` (plus `AccessDenied`).

### T-23 🧪 Persistence-layer tests ✅ COMPLETED
- `AlertJpaRepositoryHaversineIntegrationTest` verifies
  `findActiveAlertsWithinRadius` against H2 PG-mode.
- Added repository tests:
  - `AlertVoteJpaRepositoryTest` (find/delete/exists/count behavior)
  - `AlertVoteUniqueConstraintTest` (DB-level unique constraint race guard)
  - `NotificationJpaRepositoryTest` (ordering, unread, count, mark-all-read)
  - `UserJpaRepositoryTest` (`findByEmail`, `existsByEmail`)

### T-24 🧪 Integration happy-path tests for `AlertVoteController` ✅ COMPLETED
- Added `adapter/in/rest/AlertVoteControllerIntegrationTest.java` (`@SpringBootTest`)
  covering: vote → stats → switch vote → remove vote → duplicate remove 409.

### T-25 🧪 Refresh-token edge cases ✅ COMPLETED
- `AuthControllerIntegrationTest` now covers:
  expired refresh token → 401,
  access token used as refresh token → 401,
  disabled user during refresh → 401.

### T-26 🧰 Add `springdoc-openapi` ✅ COMPLETED
- Add dependency `org.springdoc:springdoc-openapi-starter-webmvc-ui`.
- Permit `/v3/api-docs/**` and `/swagger-ui/**` in `SecurityConfig`.
- Annotate request/response DTOs with minimal `@Schema` where helpful.

### T-27 🧰 Structured request/response logging ✅ COMPLETED
- Added `adapter/in/filter/RequestLoggingFilter.java` — a `@ConditionalOnProperty`
  `OncePerRequestFilter` that logs `[METHOD] /path  →  STATUS  (Nms)  [userId=...]`.
- Disabled by default; enable with `logging.nabat.request-log=true`.

### T-28 🧰 Replace `setup-db.bat` doc references ✅ COMPLETED
- Created `LOCAL_DB_SETUP.md` with full troubleshooting guide (psql not on PATH,
  auth failures, port conflicts, Docker shortcut).

### T-29 🧹 Remove the unused `WebSocketAlertNotificationAdapter` field ✅ COMPLETED
- Removed unused `notifyUser(UUID, Alert)` from `AlertNotificationPort` interface
  and its implementation in `WebSocketAlertNotificationAdapter` — it had no callers
  after T-13 wired the real subscription fan-out through `broadcastAlert`.

### T-30 🧹 Translate any remaining inline comments to English ✅ COMPLETED
- `SendNotificationUseCase.java`: translated 5 Bulgarian inline comments to English.
- `Notification.java`: renamed `createMileStoneNotification` →
  `createMilestoneNotification`; updated call site in `NotificationService`.

### T-31 🧹 Tighten `AlertVoteService.vote` consistency ✅ COMPLETED
- Added `AlertVoteUniqueConstraintTest` (`@DataJpaTest`) with 3 cases:
  duplicate (alertId, userId) → `DataIntegrityViolationException`, same user
  different alerts → OK, different users same alert → OK.

### T-32 🧹 Use `Optional<Alert>` properly in `AlertRepository.findById` ✅ COMPLETED
- Already done: `AlertRepository.findById` returns `Optional<Alert>`;
  `AlertLifecycleService` uses `.orElseThrow(() -> new AlertNotFoundException(id))`
  and `AlertVoteService` uses `.ifPresent(...)`. No changes needed.

---

## P3 — Nice-to-have / polish

> **Status (2026-05-17):** Updated after codebase re-check. `T-48` is now complete.

### T-40 📦 `.env.example` + `docker-compose` `env_file` support ⚠️ PARTIALLY COMPLETED
- Document `JWT_SECRET`, `SPRING_DATASOURCE_*`, `SERVER_PORT`,
  `NABAT_CORS_ALLOWED_ORIGINS`.
- Done part: `.env.example` exists and documents these variables.
- Remaining part: explicit `env_file` wiring is still not present in `docker-compose.yml`.

### T-41 📊 Pagination & filtering on list endpoints
- `/alerts/nearby` returns an unbounded list. Add `Pageable`, max `radiusKm`,
  optional `type` / `severity` filters.

### T-42 🗺️ Replace native Haversine with PostGIS ✅ COMPLETED
- Added `V4__postgis_spatial_indexes.sql` — conditionally enables the `postgis` extension
  and adds `GENERATED ALWAYS AS` geography columns + GiST indexes on `alerts` and
  `user_subscriptions`. The migration is a no-op when PostGIS binaries are not installed.
- `SpatialCapabilityDetector` component checks `pg_extension` at startup and activates
  either the `ST_DWithin` (indexed, fast) or Haversine (fallback) query path.
- Both `AlertJpaRepository` and `UserSubscriptionJpaRepository` carry two query methods;
  the adapters route through `SpatialCapabilityDetector.isPostgisAvailable()`.

### T-43 🐳 Switch tests to Testcontainers (PostgreSQL) ✅ COMPLETED
- `PostgresTestSupport` (new canonical base) starts a single `postgis/postgis:16-3.4`
  container per JVM via Testcontainers; `@DynamicPropertySource` injects datasource +
  Flyway settings. `@Testcontainers(disabledWithoutDocker = true)` skips gracefully.
- All `@DataJpaTest` tests now extend `PostgresTestSupport` + `@AutoConfigureTestDatabase(replace=NONE)`.
- All `@SpringBootTest` integration tests now extend `PostgresTestSupport`.
- H2 dependency removed from `pom.xml`.
- `AlertVoteUniqueConstraintTest` fixed to seed real parent records (FK compliance with PostgreSQL).
- `src/test/resources/application.properties` no longer references H2.

### T-44 🧱 Rate limit `/auth/login` and `/auth/register` ❌ REVERTED
- **Decision:** Rate limiting removed from the application layer. Will be enforced at the
  API-gateway level in a future infrastructure task.
- `RateLimitingFilter`, `bucket4j-core` dependency, and all related tests have been deleted.

### T-45 📥 Email verification & password reset flow ✅ COMPLETED
- Add `email_verified`, verification tokens, `POST /auth/verify`,
  `POST /auth/forgot-password`, `POST /auth/reset-password`. Sender abstracted
  behind a port (no SMTP coupling in domain).
- Implemented:
  - `VerificationToken` domain record + `VerificationTokenType` enum (no Spring/JPA inside domain).
  - `emailVerified` field added to `User` record with `User.verifyEmail()` factory method.
  - Out-ports: `EmailSender` + `VerificationTokenRepository`.
  - In-ports: `VerifyEmailUseCase`, `ForgotPasswordUseCase`, `ResetPasswordUseCase`.
  - `EmailVerificationService` (`@UseCase`) implements all three use cases.
  - Persistence: `VerificationTokenJpaEntity` + `VerificationTokenJpaRepository` + `VerificationTokenRepositoryAdapter`.
  - Flyway `V3__email_verification.sql` — `email_verified` column on `users`, new `verification_tokens` table.
  - `SmtpEmailSender` (`adapter/out/email`) implements `EmailSender` via `JavaMailSender`.
  - `AuthController` extended with `POST /verify`, `POST /forgot-password`, `POST /reset-password`.
  - `spring-boot-starter-mail` added to `pom.xml`; MailHog service added to `docker-compose.yml`.
  - `@MockBean EmailSender` in all 3 integration-test classes that call `/auth/register`.
  - `EmailVerificationServiceTest` (10 tests) covers all happy paths and failure branches.
  - Total: **166 tests pass**, 0 failures.

### T-46 📈 Metrics & tracing ✅ COMPLETED
- Expose `prometheus` actuator endpoint; add Micrometer timers around
  use-case services; include trace-id in error envelopes.
- Implemented:
  - `micrometer-registry-prometheus` + `micrometer-tracing-bridge-brave` + `spring-boot-starter-aop` added to `pom.xml`.
  - `management.endpoints.web.exposure.include` extended to include `prometheus`; `/actuator/prometheus` permitted without auth.
  - `UseCaseMetricsAspect` (`@Around @within(@UseCase)`) records `nabat.usecase.duration` timers with `usecase`, `method`, `outcome` tags.
  - `GlobalExceptionHandler` error envelopes now include a `traceId` field (from MDC, populated by Brave bridge — `null` when no active span).

### T-47 🧱 CI pipeline
- Audit `.github/workflows` (not yet covered): ensure `mvnw verify` runs
  on PRs with caching, and the JaCoCo gate (60%) actually fails the build.

### T-48 🗑️ Rename `Notification.createMileStoneNotification` ✅ COMPLETED
- Typo: should be `createMilestoneNotification`. Mechanical rename, update
  call sites once T-10 references it.
- Implemented in `domain/model/Notification.java`; call sites updated in `NotificationService`.

### T-49 🌐 i18n for user-visible strings ✅ COMPLETED
- Notification titles/messages are hardcoded English now (post-translation).
  Externalize to `messages.properties` so future locales drop in.
- Implemented: notification text keys moved to `src/main/resources/messages.properties` and resolved in `NotificationService` via `MessageSource` + request locale.

### T-50 🎚️ Minimum password policy
- `RegisterRequest.password` is `@Size(min = 6)`. Bump to ≥ 10, require
  mix of letters/digits, surface a clear validation error.

---

## Quick triage matrix

| ID    | Effort | Blast radius | Type        |
| ----- | ------ | ------------ | ----------- |
| T-10  | M      | medium       | feature     |
| T-11  | S      | low          | feature     |
| T-12  | S      | low          | feature     |
| T-13  | L      | medium       | feature     |
| T-14  | M      | medium       | feature     |
| T-15  | M      | low          | feature     |
| T-16  | S      | medium       | product     |
| T-20…25 | S–M  | low          | tests       |
| T-26  | S      | low          | DX          |
| T-42  | L      | high         | infra       |
| T-43  | M      | medium       | infra       |

---

If you want, point at a task ID and I'll pick it up next.







1. Secure WebSocket Connection (Authentication Domain)
   Context: The AGENTS.md explicitly calls out a technical debt item: AlertWebSocketHandler currently trusts ?userId=. In a production safety system, spoofing users can lead to false alert broadcasts or unauthorized data access. Task:

Implement a ticket-based authentication flow for WebSockets, or parse the JWT from an Authorization header during the initial HTTP upgrade request.
Create a new @UseCase (e.g., IssueWebSocketTicketUseCase) in the application layer.
Update adapter/in/websocket/AlertWebSocketHandler to validate the token/ticket before establishing the session. Trade-offs:
Ticket-based vs JWT in Query Param: Passing JWTs in query params during WS handshake logs them in access logs (security risk). A short-lived ticket system requires an extra HTTP round-trip (higher latency) but keeps the JWT out of logs and URL history.
2. Introduce Domain Events for Alert Notifications (Core Domain)
   Context: When an Alert is created or its credibility score changes, users need to be notified in real-time. Direct calls from the CreateAlertService to the WebSocket or Push Notification adapters tightly couples the domains. Task:

Introduce an Event-Driven pattern. When Alert.create() happens, return a domain event (e.g., AlertCreatedEvent).
Publish this event via Spring's ApplicationEventPublisher (acting as an in-memory message bus for now).
Create an Event Listener in the application layer that triggers the AlertNotificationPort to broadcast to WebSockets. Trade-offs:
Coupling vs Traceability: Event-driven decoupling makes the core domain pure and easy to test, but it makes the execution flow harder to trace sequentially. We trade cognitive simplicity for architectural flexibility.
3. Optimize Spatial Queries for Proximity (Persistence Adapter)
   Context: The current system uses a native Haversine formula in PostgreSQL. As the platform grows, computing Haversine for every alert against the user's location on every request will cause severe CPU bottlenecks on the database. Task:

Add a spatial index to the AlertJpaEntity using Postgres earthdistance module (since PostGIS might be overkill initially, but earthdistance is built-in).
Update V3__spatial_indexes.sql via Flyway.
Rewrite the native query in AlertRepositoryAdapter to utilize the bounding box (<@>) operator instead of raw Haversine math. Trade-offs:
Write speed vs Read speed: Adding spatial indexes slows down INSERT operations slightly (which happen during emergencies) but drastically speeds up SELECT operations (which happen constantly for all users viewing the map).
🌐 Frontend Tasks (nabat-fe)
1. Implement Token Lifecycle & WebSocket Handshake (Auth Module)
   Context: The backend strictly enforces JWTs and rejects API auth via refresh tokens. The frontend must seamlessly manage this without interrupting the user's real-time feed. Task:

Implement an Axios (or Fetch) interceptor to handle 401s, silently refresh the JWT using the refresh token, and retry the failed request.
Implement the WebSocket connection manager. If using the ticket-based auth designed in Backend Task #1, the FE must request a short-lived ticket via HTTP, then open the WebSocket with that ticket. Trade-offs:
Complexity vs Security: Managing silent token refreshes and WS reconnections adds significant state-management complexity to the frontend, but it provides a seamless user experience while adhering to strict backend security boundaries.
2. Real-Time Map & Feed Synchronization (Alerts Context)
   Context: The core value proposition is seeing nearby alerts in real-time. The frontend needs to merge REST API data (initial load) with WebSocket data (live updates). Task:

Create a dedicated state store (e.g., Redux, Zustand, or React Context) for Alerts.
On mount, fetch /api/v1/alerts/nearby via REST.
Listen to WebSocket events (ALERT_CREATED, ALERT_RESOLVED, SCORE_UPDATED). Upsert or remove these alerts in the local state store dynamically. Trade-offs:
Client-side state vs Server truth: We are duplicating domain state on the client. If the WebSocket drops, the client's map becomes stale. We trade memory and state duplication for a highly responsive, zero-latency UI.
3. Optimistic UI for Alert Voting (Engagement Context)
   Context: Users will vote to impact the credibility score of an alert. Waiting for a server round-trip to show the vote UI changing makes the app feel sluggish during high-stress situations. Task:

Implement Optimistic UI updates when calling the vote endpoint.
Immediately increment/decrement the score locally and highlight the vote button.
Revert the local state and display a non-intrusive toast error if the backend returns a 400/409 (e.g., GlobalExceptionHandler's MSG_INVALID_REQUEST). Trade-offs:
Perceived Performance vs Data Consistency: The user feels the app is instantly responsive, but there is a risk of temporary "UI lying" if the network request ultimately fails.
