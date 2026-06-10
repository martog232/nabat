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
│  DB-less mode — declarative config in kong.yml               │
│                                                              │
│  Routes:                                                     │
│    /api/v1/*  ────────────► nabat-service (:8080)            │
│    /ws/*      ────────────► nabat-service (:8080)            │
│    ~/api/v1/alerts/[^/]+/votes ──► nabat-voting-service (:8081) │
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

```
Browser → POST /api/v1/alerts/{id}/votes ──► nabat-app AlertVoteController
                                                  │
                                                  ▼
                                             ExternalVoteService.vote()
                                                  │
                                                  ├──► externalVotingPort.vote()
                                                  │       │
                                                  │       └──► HTTP POST
                                                  │             nabat-voting:8081
                                                  │             /api/v1/alerts/{id}/votes
                                                  │                  │
                                                  │                  ▼
                                                  │             VoteController
                                                  │                  │
                                                  │                  ▼
                                                  │             CastVoteService
                                                  │                  │
                                                  │                  ├──► voteRepository.save()
                                                  │                  │       └──► Postgres (nabat_voting_db)
                                                  │                  │
                                                  │                  └──► kafkaVoteEventPublisher
                                                  │                          .publish()
                                                  │                          └──► Kafka topic: vote.cast
                                                  │
                                                  ├──► syncProjection()
                                                  │       │
                                                  │       └──► HTTP GET nabat-voting:8081
                                                  │             /api/v1/alerts/{id}/votes/stats
                                                  │                  │
                                                  │                  ▼
                                                  │             returns upvotes/downvotes/confirmations
                                                  │                  │
                                                  │                  ▼
                                                  │             alertRepository.updateVoteCounts()
                                                  │               └──► update alerts table (nabat_db)
                                                  │
                                                  └──► notifyAlertOwner()
                                                          │
                                                          └──► NotificationService
                                                                  ├──► persist Notification
                                                                  └──► WebSocket push to owner
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

| Purpose       | Mechanism        | Key/Channel            | Notes                          |
|---------------|------------------|------------------------|--------------------------------|
| WebSocket Pub/Sub | Redis Pub/Sub | `ws:alerts`           | Cross-instance WS message relay |
| Near-cache    | Cache-aside      | `nearbyAlerts::key`    | TTL 15s, JSON-serialized       |

## Database layout

| Service    | Database         | Port  | Key tables                           |
|------------|------------------|-------|--------------------------------------|
| nabat-app  | nabat_db         | 5433  | users, alerts, user_subscriptions, notifications |
| nabat-voting | nabat_voting_db | 5434  | votes                                |

- `alerts` table has denormalized vote count columns (`upvote_count`, `downvote_count`, `confirmation_count`, `credibility_score`) updated by `ExternalVoteService.syncProjection()` after each vote.
- Vote persistence is owned by `nabat-voting` only. The original `alert_votes` table in `nabat_db` was dropped by migration V7.

## Kong routing

| Path pattern         | Upstream          | Priority | Strip path |
|----------------------|-------------------|----------|------------|
| `/api/v1`            | nabat-app:8080    | 0        | No         |
| `/ws`                | nabat-app:8080    | 0        | No         |
| `~/api/v1/alerts/[^/]+/votes` | nabat-voting-app:8081 | 100 | No |

The regex route for `/votes` has higher priority and matches before the prefix route for `/api/v1`.

## Kafka topics

| Topic       | Publisher              | Consumer                | Purpose                          |
|-------------|------------------------|-------------------------|----------------------------------|
| `vote.cast` | nabat-voting (KafkaVoteEventPublisher) | nabat-voting (KafkaVoteEventConsumer) | Credibility projection recalculation |

## Debugging

Remote JVM debug ports (when enabled in docker-compose):
- nabat-app: `:5005`
- nabat-voting-app: `:5006`
