# AGENTS.md — Nabat backend

Spring Boot 3.4 / Java 21 real-time safety-alert service. Hexagonal (ports & adapters) layout under `org.example.nabat`. Read `README.md` for product context; this file is the agent-facing cheat sheet.

## Architecture rules (non-obvious)

- Layers under `src/main/java/org/example/nabat/`:
  - `domain/model` — pure Java **records** + value-object IDs (`AlertId`, `UserId`, ...) and enums. No Spring/JPA/Lombok annotations here. Domain logic lives on records (e.g. `Alert.create(...)`, `Alert.resolve()`, `Alert.getCredibilityScore()`).
  - `application/port/in` — use-case interfaces (one method, plus a nested `Command` record when needed, e.g. `CreateAlertUseCase.CreateAlertCommand`).
  - `application/port/out` — driven ports (`AlertRepository`, `UserSubscriptionRepository`, `AlertNotificationPort`, `TokenProvider`, ...). Implemented in `adapter/out/**`.
  - `application/service` — use-case implementations. **Always annotate with `@UseCase`** (custom stereotype in `application/UseCase.java`). Don’t use `@Service` and don’t register them in `UseCaseConfig` — `UseCaseConfig` does a filtered component scan and explicit `@Bean`s would create duplicates.
  - `adapter/in/rest` — `@RestController` + request/response DTO records under same package; cross-cutting errors handled by `GlobalExceptionHandler`.
  - `adapter/in/security` — `SecurityConfig`, `JwtAuthenticationFilter`, `JwtTokenProvider`, `LoginAttemptTracker`.
  - `adapter/in/websocket` — `AlertWebSocketHandler` (currently trusts `?userId=` — see Roadmap in README).
  - `adapter/out/persistence` — `*JpaEntity` (Lombok `@Getter/@Setter`, protected no-arg ctor) + `*JpaRepository` (Spring Data) + `*RepositoryAdapter` (`@Component` implementing the out-port). Mapping is **manual** via static `from(domain)` and instance `toDomain()` methods — keep entities and domain records decoupled.

- Persistence is PostgreSQL with **Flyway** (`src/main/resources/db/migration/V1__schema.sql`, `V2__seed_data.sql`). `spring.jpa.hibernate.ddl-auto=validate` — never let Hibernate auto-DDL; schema changes go through a new `V<n>__*.sql` migration. Tests run against H2 in PostgreSQL compatibility mode (`src/test/resources/application.properties`), so SQL must be cross-compatible (the nearby-alerts query uses a native Haversine — keep it portable).

## Conventions

- New use case = new in-port interface in `port/in` + new `@UseCase` service in `application/service`. Wire only via constructor injection of out-ports (see `CreateAlertService`).
- New persistence type = entity + Spring Data repo + adapter implementing the out-port. Adapters are `@Component`, not `@Repository`.
- REST DTOs are records (`CreateAlertRequest`, `AlertResponse`, ...) co-located with controllers. Validate with `jakarta.validation` annotations; `MethodArgumentNotValidException` is mapped to `{status, message:"Validation failed", errors:{field:msg}, timestamp}` by `GlobalExceptionHandler`.
- Throw plain `IllegalArgumentException` (→400), `IllegalStateException` (→409), `AlertNotFoundException` (→404), `BadCredentialsException` (→401), `AccessDeniedException` (→403). **Messages are NOT echoed to clients** — `GlobalExceptionHandler` returns curated constants (`MSG_INVALID_REQUEST`, etc.) and logs the original. Don’t put user-facing detail in exception messages.
- All HTTP routes are under `/api/v1`. `/api/v1/auth/**` is open; everything else requires `Authorization: Bearer <accessToken>`. JWT filter sets authorities as `ROLE_<role>` and looks up users by stable `userId` claim, not email. Refresh tokens are rejected for API auth (token-type claim enforced).
- Config is environment-variable driven; **no Spring profiles**. Defaults live in `application.properties`. `JWT_SECRET` must be ≥32 chars and must not contain `change-me-before-production` (the app fails fast otherwise).
- CORS origins come from `nabat.cors.allowed-origins` (comma-separated, empty by default).

## Workflows (PowerShell — Windows is the dev OS)

```powershell
.\mvnw.cmd test                              # unit + slice + @SpringBootTest (H2, no Docker needed)
.\mvnw.cmd "-Dtest=AlertVoteServiceTest" test  # single test (quotes required in PowerShell)
.\mvnw.cmd clean package                     # builds jar + runs JaCoCo (fails <60% LINE BUNDLE coverage)
.\mvnw.cmd spring-boot:run                   # run app; needs Postgres on 127.0.0.1:5432 (or set SPRING_DATASOURCE_URL)
docker compose up -d postgres                # dev DB on host port 5433 (note: not 5432)
docker compose up --build                    # full stack on :8080
```

- Use `127.0.0.1`, not `localhost`, in URLs (Windows IPv6 quirk — applied throughout the codebase).
- Coverage report: `target/site/jacoco/index.html`. JaCoCo excludes `**/config/**` and REST `*Request*`/`*Response*` DTOs — don’t add logic to those packages expecting it to count.
- Checkstyle (`config/checkstyle/checkstyle.xml`) runs but is `failOnViolation=false`; treat warnings as advisory.

## Testing patterns

- Service tests: plain JUnit 5 + Mockito on the `@UseCase` class, mocking out-ports.
- Controller tests: `@WebMvcTest` with security filters disabled (see existing `*ControllerTest`).
- End-to-end: `@SpringBootTest` + H2 (e.g. `AuthControllerIntegrationTest`, `AlertControllerIntegrationTest`). No Testcontainers.
- Known untested areas (Roadmap): `NotificationService`, `JwtAuthenticationFilter`, `GlobalExceptionHandler`, `AlertWebSocketHandler`, native Haversine query.

## Things that look done but aren’t (don’t assume they work)

- `SubscribeToAlertsUseCase` has no implementation; `UserSubscriptionRepository.findUsersSubscribedToAlertType` returns `[]`, so `CreateAlertService` never actually fans out WebSocket pushes.
- `NotificationService.sendVoteNotification` / `sendMilestoneNotification` are stubs returning `null`; no controller wires `GetNotificationUseCase`.
- `AlertStatus.RESOLVED` / `Alert.resolve()` exist but no endpoint calls it.
- `Role.ADMIN` is seeded; nothing checks it (no `@PreAuthorize` anywhere in the codebase).
- `POST /api/v1/alerts` currently trusts `reportedBy` from the body instead of the JWT principal.
- WebSocket handshake (`/ws/alerts?userId=...`) is unauthenticated.

