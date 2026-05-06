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

### T-20 🧪 Tests for `NotificationService`
- New file `application/service/NotificationServiceTest.java` covering:
  `markAsRead` happy path, already-read short-circuit, wrong-user 400,
  `markAllAsRead` delegation, list/unread/count delegation.

### T-21 🧪 Tests for `JwtAuthenticationFilter`
- **Note:** Filter has been hardened (2026-04-27) with token type validation, userId-based lookup, and security context cleanup.
- Cover: no header → next filter, invalid bearer → 401, valid bearer →
  principal is set to the loaded `User`, disabled user → 401, **refresh token used as access → 401**, **user lookup by userId (not email)**.

### T-22 🧪 Tests for `GlobalExceptionHandler`
- One test per handler branch (`BadCredentials`, `IllegalArgument`,
  `IllegalState`, `AlertNotFound`, `MethodArgumentNotValid`).

### T-23 🧪 Persistence-layer tests
- `@DataJpaTest` for `AlertJpaRepository.findActiveAlertsWithinRadius` against
  H2 in PG-mode (verifies the native Haversine query keeps working).
- Repository tests for `AlertVoteJpaRepository` (now-fixed UUID id),
  `NotificationJpaRepository`, `UserJpaRepository`.

### T-24 🧪 Integration happy-path tests for `AlertVoteController`
- Today only `@WebMvcTest`. Add `@SpringBootTest` covering:
  vote → stats → switch vote → remove vote → 409 on duplicate.

### T-25 🧪 Refresh-token edge cases
- Expired refresh token → 401, access token used as refresh → 401,
  disabled user during refresh → 401.

### T-26 🧰 Add `springdoc-openapi`
- Add dependency `org.springdoc:springdoc-openapi-starter-webmvc-ui`.
- Permit `/v3/api-docs/**` and `/swagger-ui/**` in `SecurityConfig`.
- Annotate request/response DTOs with minimal `@Schema` where helpful.

### T-27 🧰 Structured request/response logging
- Add a `RequestLoggingFilter` (commons or a small custom one) that logs
  method, path, status, duration with the user id when available. Disabled
  by default; enabled via `logging.nabat.request-log=true`.

### T-28 🧰 Replace `setup-db.bat` doc references
- `setup-db.bat` and `README` historically referenced `LOCAL_DB_SETUP.md` /
  `DATABASE_SETUP.md` which don't exist. Either create those files or
  remove the references from the script. Already cleaned in `README`.

### T-29 🧹 Remove the unused `WebSocketAlertNotificationAdapter` field
- The adapter holds a `userSubscriptionRepository`-shaped chain to
  `AlertWebSocketHandler.sendAlertToUser`. Once T-13 lands, audit for unused
  collaborators and remove dead code.

### T-30 🧹 Translate any remaining inline comments to English
- `Notification.createMileStoneNotification` (note method-name typo "Mile**S**tone"
  → consider renaming to `createMilestoneNotification`).
- Audit `application/port/in/*.java` and `domain/model/*.java` for stragglers.

### T-31 🧹 Tighten `AlertVoteService.vote` consistency
- Wrap `find → delete → save → updateVoteCounts` is now in `@Transactional`
  (done) — add a unique-constraint test confirming the
  `uk_alert_votes_alert_user` is hit on race.

### T-32 🧹 Use `Optional<Alert>` properly in `AlertRepository.findById`
- Currently unused. After T-14 lands, make sure adapters return `Optional`
  and the service throws `AlertNotFoundException` on miss.

---

## P3 — Nice-to-have / polish

### T-40 📦 `.env.example` + `docker-compose` `env_file` support
- Document `JWT_SECRET`, `SPRING_DATASOURCE_*`, `SERVER_PORT`,
  `NABAT_CORS_ALLOWED_ORIGINS`.

### T-41 📊 Pagination & filtering on list endpoints
- `/alerts/nearby` returns an unbounded list. Add `Pageable`, max `radiusKm`,
  optional `type` / `severity` filters.

### T-42 🗺️ Replace native Haversine with PostGIS
- Add the `postgis` Postgres extension and `geometry(Point, 4326)` column;
  use `ST_DWithin`. Keep the H2-friendly query path behind a profile or
  drop H2 in favor of Testcontainers.

### T-43 🐳 Switch tests to Testcontainers (PostgreSQL)
- Removes the H2/PG-mode dialect drift risk and lets us use PostGIS in tests.

### T-44 🧱 Rate limit `/auth/login` and `/auth/register`
- Bucket4j or Spring's `RateLimiter`. Protects against brute-force.

### T-45 📥 Email verification & password reset flow
- Add `email_verified`, verification tokens, `POST /auth/verify`,
  `POST /auth/forgot-password`, `POST /auth/reset-password`. Sender abstracted
  behind a port (no SMTP coupling in domain).

### T-46 📈 Metrics & tracing
- Expose `prometheus` actuator endpoint; add Micrometer timers around
  use-case services; include trace-id in error envelopes.

### T-47 🧱 CI pipeline
- Audit `.github/workflows` (not yet covered): ensure `mvnw verify` runs
  on PRs with caching, and the JaCoCo gate (60%) actually fails the build.

### T-48 🗑️ Rename `Notification.createMileStoneNotification`
- Typo: should be `createMilestoneNotification`. Mechanical rename, update
  call sites once T-10 references it.

### T-49 🌐 i18n for user-visible strings
- Notification titles/messages are hardcoded English now (post-translation).
  Externalize to `messages.properties` so future locales drop in.

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

