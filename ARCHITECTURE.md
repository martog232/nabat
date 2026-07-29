# Architecture — nabat

## High-level overview

```
┌──────────────────────────────────────────────────────────────┐
│                        Browser (FE)                          │
│  Vite dev :5173 or production build                          │
│  Connects via VITE_API_BASE_URL (default http://127.0.0.1:8080/api/v1) │
└──────┬──────────────────────┬──────────────────────┬─────────┘
       │ REST                 │ WebSocket            │
       │ (axios)              │ (ws://.../ws/alerts) │
       ▼                      ▼                      ▼
┌──────────────────────────────────────────────────────────────┐
│              Kong API Gateway (:8000 / :8001)                │
│  DB-less mode — declarative config lives in a SEPARATE repo  │
│                                                              │
│  Routes:                                                     │
│    /api/v1/*  ────────────► nabat-service (:8080)            │
│    /ws/*      ────────────► nabat-service (:8080)            │
│                                                              │
│  Also owns rate limiting / brute-force protection for the    │
│  platform — nabat-app does not throttle in-process.          │
│                                                              │
│  Note: FE dev server proxies /api → 127.0.0.1:8080 directly │
│        (bypasses Kong). Kong is used in production/staging.  │
└──────┬──────────────────────┬────────────────────────────────┘
       │                      │
       ▼                      ▼
┌─────────────────┐  ┌──────────────────────────┐
│   nabat-app     │  │   nabat-voting-app        │
│   Java 21       │  │   Java 21                 │
│   :8080         │  │   :8081                   │
│                 │  │                           │
│   ┌──────────┐  │  │   ┌──────────────────┐    │
│   │ REST     │  │  │   │ POST/GET/DELETE  │    │
│   │ Controlls│  │  │   │ /api/v1/alerts/  │    │
│   │          │  │  │   │   {id}/votes     │    │
│   │ /auth/*  │  │  │   └────────┬─────────┘    │
│   │ /users/* │  │  │            │               │
│   │ /alerts/*│──┼──┼───HTTP────► CastVoteService│
│   │ /ws/tickets│  │  │            │               │
│   └─────┬────┘  │  │            │               │
│         │       │  │            ▼               │
│   ┌─────▼────┐  │  │  ┌──────────────────┐    │
│   │External  │  │  │  │ PostgresVoteRepo  │    │
│   │VoteService│  │  │  │ (own DB :5434)   │    │
│   │          │  │  │  └──────────────────┘    │
│   │ syncProject│  │  │            │               │
│   │ notifyOwner│  │  │            ▼               │
│   └──────────┘  │  │  ┌──────────────────┐    │
│                 │  │  │ KafkaVoteEventPub │    │
│   ┌──────────┐  │  │  │ topic: vote.cast │    │
│   │ AlertRepo│  │  │  └────────┬─────────┘    │
│   │ (own DB  │  │  │           │               │
│   │  :5433)  │  │  │           ▼               │
│   │ denorm   │  │  │  ┌──────────────────┐    │
│   │ vote cts │  │  │  │ KafkaVoteEventCon │    │
│   └──────────┘  │  │  │ + CredibilityProj │    │
│                 │  │  └──────────────────┘    │
│   ┌──────────┐  │  │                           │
│   │WebSocket │  │  │                           │
│   │Handler   │  │  │                           │
│   └──────────┘  │  │                           │
└─────────────────┘  └──────────────────────────┘    ┌──────────────┐
│                              │    │   nabat-redis │
│                              │    │   (Pub/Sub +  │
│                              │    │    cache)     │
│                              │    │   :6379       │
│                              │    └──────────────┘
│                              │         ▲
│                              │         │ ws:alerts
│                              └─────────┘
```

## Request flows

### 1. Alert creation

```
Browser → POST /api/v1/alerts  ──► nabat-app AlertController
                                       │
                                       ▼
                                  CreateAlertService
                                       │
                                       ├──► alertRepository.save()
                                       │       └──► Postgres (nabat_db)
                                       │
                                       ├──► subscriptionRepository
                                       │       .findUsersSubscribedToAlertType()
                                       │
                                       ├──► userRepository
                                       │       .findUsersNearLocation()
                                       │
                                       └──► notificationPort.broadcastAlert()
                                               └──► WebSocket push to subscribers
```

### 2. Voting (frontend → nabat-app → nabat-voting)

> nabat-app forwards **the caller's own access token** to nabat-voting, which derives
> the voter from its `userId` claim. `POST .../votes` and `DELETE .../votes` return the
> resulting tallies, read from the voting service's write model inside its own
> transaction — the `/votes/stats` endpoint is served from the asynchronously-updated
> projection and cannot be used for read-your-writes.

```
Browser → POST /api/v1/alerts/{id}/votes ──► nabat-app AlertVoteController
                                                  │
                                                  ▼
                                             ExternalVoteService.vote()
                                                  │
                                                  ├──► externalVotingPort.vote()
                                                  │       │
                                                  │       └──► HTTP POST (caller's bearer token)
                                                  │             nabat-voting:8081
                                                  │             /api/v1/alerts/{id}/votes
                                                  │                  │
                                                  │                  ▼
                                                  │             VoteController
                                                  │               voter = token's userId claim
                                                  │                  │
                                                  │                  ▼
                                                  │             CastVoteService (one transaction)
                                                  │                  │
                                                  │                  ├──► voteRepository.save()
                                                  │                  │       └──► Postgres (nabat_voting_db)
                                                  │                  │
                                                  │                  ├──► voteRepository.countsFor()
                                                  │                  │       └──► fresh tallies, returned
                                                  │                  │            in the response body
                                                  │                  │
                                                  │                  └──► kafkaVoteEventPublisher
                                                  │                          .publish()
                                                  │                          └──► Kafka topic: vote.cast
                                                  │                                  (updates the read-model
                                                  │                                   asynchronously)
                                                  │
                                                  ├──► syncProjection(stats from the response)
                                                  │       └──► alertRepository.applyVoteCounts()
                                                  │             update + reread, one short transaction,
                                                  │             outside the HTTP call
                                                  │
                                                  ├──► notifyAlertOwner()
                                                  │       └──► NotificationService
                                                  │               ├──► persist Notification
                                                  │               └──► WebSocket push to owner
                                                  │
                                                  └──► broadcastAlertUpdate()
```

### 3. Nearby alerts (read-heavy)

```
Browser → GET /api/v1/alerts/nearby?lat=...&lng=...&radius=...
                    │
                    ▼
               nabat-app AlertController
                    │
                    ▼
               alertRepository.findActiveAlertsWithinRadius()
                    │
                    └──► PostGIS ST_DWithin query (nabat_db)
                         returns Alert with denormalized vote counts
```

### 4. WebSocket (real-time alerts)

```
Browser → ws://host/ws/alerts?ticket=<ticket>
                    │
                    ▼
               nabat-app AlertWebSocketHandler
                    │
                    ├── JwtHandshakeInterceptor validates ticket
                    │
                    └── on NEW_ALERT event:
                         sendAlertToUser(userId, AlertResponse)
                           ├── if user connected locally → deliver directly
                           └── if user NOT connected locally → publish to Redis channel ws:alerts
                                    │
                                    ▼
                              All nabat-app instances
                                    │
                                    ├── RedisWsSubscriber receives message
                                    └── calls deliverLocally() on local session map
                                          └── Websocket frame: { type: "NEW_ALERT", alert: AlertResponse }
```

### 5. Nearby alerts (cached)

```
Browser → GET /api/v1/alerts/nearby?lat=...&lng=...&radius=...
                    │
                    ▼
               nabat-app AlertController
                    │
                    ▼
               GetNearbyAlertsService (🔵 @Cacheable "nearbyAlerts")
                    │
                    ├── cache MISS → alertRepository.findActiveAlertsWithinRadius()
                    │       │            └── PostGIS ST_DWithin query
                    │       └── result stored in Redis with TTL (default 15s)
                    │
                    └── cache HIT → return cached List<Alert> from Redis
```

## Redis

| Purpose            | Mechanism     | Key/Channel                     | Notes |
|--------------------|---------------|---------------------------------|-------|
| WebSocket Pub/Sub  | Redis Pub/Sub | `ws:alerts`                     | Cross-instance WS relay. Frames carry an `origin` instance id so a publisher ignores its own echo. |
| Near-cache         | Cache-aside   | `nearbyAlerts::<lat>_<lng>_<r>` | TTL 15s. Coordinates are quantized to ~110 m so nearby requests share an entry; evicted whenever an alert is created or resolved. |
| WebSocket tickets  | Expiring key  | `ws:ticket:<value>`             | Single-use, consumed with `GETDEL`. In Redis rather than in memory so any replica can redeem a ticket issued by another. |
| Refresh-token reuse | Expiring key | `auth:refresh:consumed:<jti>`   | Marks a refresh token as exchanged, giving single-use rotation with replay detection across replicas. |

## Database layout

| Service    | Database         | Port  | Key tables                           |
|------------|------------------|-------|--------------------------------------|
| nabat-app  | nabat_db         | 5433  | users, alerts, user_subscriptions, notifications |
| nabat-voting | nabat_voting_db | 5434  | votes                                |

- `alerts` has denormalized vote-count columns (`upvote_count`, `downvote_count`,
  `confirmation_count`, `credibility_score`) written by `ExternalVoteService` from the
  tallies nabat-voting returns. `credibility_score` has exactly one author — the voting
  service — and is carried through the domain unchanged; it is never recomputed locally.
- `alerts.version` provides optimistic locking, so a concurrent resolve and vote-count
  sync cannot silently overwrite each other.
- Vote persistence is owned by `nabat-voting` only. The original `alert_votes` table in `nabat_db` was dropped by migration V7.

## Kong routing

The declarative Kong config lives in its own repository, not here.

| Path pattern | Upstream       | Strip path |
|--------------|----------------|------------|
| `/api/v1`    | nabat-app:8080 | No         |
| `/ws`        | nabat-app:8080 | No         |

**Votes must not be routed directly to nabat-voting.** An earlier version of this
document described a higher-priority regex route sending
`~/api/v1/alerts/[^/]+/votes` straight to `nabat-voting-app:8081`. That would bypass
`ExternalVoteService` entirely, and with it the denormalised vote-count sync on
`alerts`, the owner/milestone notifications, and the `ALERT_UPDATED` WebSocket
broadcast — so votes would land in the voting database and nothing else in the system
would ever hear about them. Vote traffic goes to nabat-app, which calls nabat-voting
and forwards the caller's own access token.

Rate limiting and brute-force protection are Kong's responsibility. nabat-app's
`LoginAttemptTracker` only observes and logs failed logins; it does not block.

## Kafka topics

| Topic          | Publisher    | Consumer     | Purpose |
|----------------|--------------|--------------|---------|
| `vote.cast`    | nabat-voting | nabat-voting | Credibility projection recalculation |
| `vote.removed` | nabat-voting | nabat-voting | Same, on vote removal |

Both are keyed by alert id so events for one alert keep their order. Replica count is
configurable via `NABAT_KAFKA_TOPIC_REPLICAS` and defaults to 1, matching the
single-broker clusters used in docker-compose and the Helm chart.

The publish is a dual write: the database transaction and the Kafka send are not
atomic, so a crash between commit and send loses the event. Send failures are logged
loudly, and the projection can be rebuilt from the write model at any time via
`POST /api/v1/admin/credibility/rebuild` (ADMIN only). A transactional outbox is the
proper fix and is not yet implemented.

## Debugging

Remote JVM debugging is **off by default** — an open JDWP port is remote code
execution, so it is not something to ship enabled. Turn it on for a local session:

```bash
# nabat-voting
NABAT_VOTING_DEBUG_OPTS='-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5006' \
  docker compose up
```

Ports, when enabled (bound to loopback only):
- nabat-app: `:5005`
- nabat-voting-app: `:5006`
