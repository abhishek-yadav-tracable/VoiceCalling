# Voice Campaign Service

An outbound voice campaign microservice that provides APIs to trigger calls, manage campaigns, and track call statuses. Built with Java 17, Spring Boot 3.2, PostgreSQL, and Redis. Includes a React-based UI for campaign management.

**This is built using Windsurf AI and Claude Opus for writing code**

## Quick Start with Docker

```bash
# Start all services (API, UI, PostgreSQL, Redis)
docker-compose up --build

# Access the UI at http://localhost:3000
# API is available at http://localhost:8081
```

## System Architecture

### High-Level Architecture

```
┌─────────────────────────────────────────────────────────────────────────────────┐
│                                 CLIENTS                                          │
│                    ┌──────────────┐    ┌──────────────┐                         │
│                    │   React UI   │    │  REST API    │                         │
│                    │  (Port 3000) │    │   Clients    │                         │
│                    └──────┬───────┘    └──────┬───────┘                         │
└───────────────────────────┼───────────────────┼─────────────────────────────────┘
                            │                   │
                            ▼                   ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              API LAYER (Port 8080)                               │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐  ┌────────────┐ │
│  │ CampaignController│ │ CallController  │  │MetricsController│  │Rate Limiter│ │
│  │ - CRUD campaigns │  │ - Single calls  │  │ - Global stats  │  │ (Bucket4j) │ │
│  │ - Start/Pause    │  │ - Callbacks     │  │ - Worker util   │  │            │ │
│  └────────┬─────────┘  └────────┬────────┘  └────────┬────────┘  └────────────┘ │
└───────────┼─────────────────────┼───────────────────┼───────────────────────────┘
            │                     │                   │
            ▼                     ▼                   ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              SERVICE LAYER                                       │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐                  │
│  │ CampaignService │  │   CallService   │  │  TelephonyService│                 │
│  │ - Validation    │  │ - Execute calls │  │ - Circuit breaker│                 │
│  │ - Phone import  │  │ - Handle callback│ │ - Mock provider  │                 │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘                  │
└─────────────────────────────────────────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                           WORKER POOL ARCHITECTURE                               │
│                                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                     CallQueueService (@Scheduled 1s)                     │   │
│  │  1. Fetch IN_PROGRESS campaigns                                          │   │
│  │  2. Filter by business hours (timezone-aware)                            │   │
│  │  3. Fair distribution (round-robin across campaigns)                     │   │
│  │  4. Enqueue RETRIES first, then PENDING calls                           │   │
│  │  5. Respect per-campaign concurrency limits                              │   │
│  └──────────────────────────────────┬──────────────────────────────────────┘   │
│                                     │                                           │
│                                     ▼                                           │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                    Redis Queue (call:queue)                              │   │
│  │                         FIFO Job Queue                                   │   │
│  └──────────────────────────────────┬──────────────────────────────────────┘   │
│                                     │                                           │
│                                     ▼                                           │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                    CallWorkerPool (20 threads)                           │   │
│  │  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐        ┌────────┐          │   │
│  │  │Worker 1│ │Worker 2│ │Worker 3│ │Worker 4│  ...   │Worker N│          │   │
│  │  └────┬───┘ └────┬───┘ └────┬───┘ └────┬───┘        └────┬───┘          │   │
│  │       └──────────┴──────────┴──────────┴─────────────────┘              │   │
│  │                          BLPOP (blocking)                                │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
│                                                                                  │
│  ┌─────────────────────────────────────────────────────────────────────────┐   │
│  │                    CallbackWatchdog (@Scheduled 30s)                     │   │
│  │  - Detects calls with missing callbacks (timeout)                        │   │
│  │  - Marks as FAILED and schedules retry                                   │   │
│  └─────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────┘
            │
            ▼
┌─────────────────────────────────────────────────────────────────────────────────┐
│                              DATA LAYER                                          │
│  ┌─────────────────────────────┐    ┌─────────────────────────────┐             │
│  │        PostgreSQL           │    │           Redis              │             │
│  │  - campaigns table          │    │  - call:queue (job queue)    │             │
│  │  - call_requests table      │    │  - campaign:slots:* (active) │             │
│  │  - Persistent storage       │    │  - campaign:queued:* (queued)│             │
│  │                             │    │  - worker:active_count       │             │
│  └─────────────────────────────┘    └─────────────────────────────┘             │
└─────────────────────────────────────────────────────────────────────────────────┘
```

### Call Flow Sequence

```
┌──────┐     ┌─────────┐     ┌───────────┐     ┌─────────┐     ┌──────────┐
│Client│     │   API   │     │QueueService│    │WorkerPool│    │Telephony │
└──┬───┘     └────┬────┘     └─────┬─────┘     └────┬────┘     └────┬─────┘
   │              │                │                │               │
   │ Create Campaign               │                │               │
   │─────────────>│                │                │               │
   │              │                │                │               │
   │ Start Campaign                │                │               │
   │─────────────>│                │                │               │
   │              │                │                │               │
   │              │  @Scheduled    │                │               │
   │              │  (every 1s)    │                │               │
   │              │                │                │               │
   │              │    Enqueue calls to Redis       │               │
   │              │───────────────>│                │               │
   │              │                │                │               │
   │              │                │  BLPOP job     │               │
   │              │                │<───────────────│               │
   │              │                │                │               │
   │              │                │                │ Initiate call │
   │              │                │                │──────────────>│
   │              │                │                │               │
   │              │                │                │  externalId   │
   │              │                │                │<──────────────│
   │              │                │                │               │
   │              │                │                │ (3-10s later) │
   │              │                │                │   Callback    │
   │              │<───────────────────────────────────────────────│
   │              │                │                │               │
   │              │ Update status  │                │               │
   │              │ Release slot   │                │               │
   │              │                │                │               │
```

## Requirements Compliance

| Requirement | Status | Implementation |
|-------------|--------|----------------|
| **1. API to trigger outbound call** | ✅ | `POST /api/v1/calls` - Single call API |
| **2. API to check call status** | ✅ | `GET /api/v1/calls/{id}` - Returns status, duration, failure reason |
| **3. Campaign management** | ✅ | Full CRUD + start/pause/cancel via `CampaignController` |
| **4. Business hour scheduling** | ✅ | `BusinessHours` with timezone, start/end time, allowed days |
| **5. Timezone handling** | ✅ | Uses `ZonedDateTime` with campaign's configured timezone |
| **6. Concurrency control** | ✅ | Per-campaign limits via Redis slots, default limit configurable |
| **7. Retry handling** | ✅ | Exponential backoff (sync) + fixed delay (callback failures) |
| **8. Retries before new calls** | ✅ | `CallQueueService.enqueueRetries()` called before `enqueuePendingCalls()` |
| **9. Status tracking** | ✅ | Individual call status + campaign status + aggregated metrics |
| **10. Rate limiting** | ✅ | Bucket4j filter (100 req/sec per IP) |
| **11. Circuit breaker** | ✅ | Resilience4j on `TelephonyService` |
| **12. Mock telephony** | ✅ | Async callbacks, configurable failure rates (5% fail, 1% timeout) |
| **13. Large ingestion (1K-100K)** | ✅ | Batch insert, file upload, deduplication |
| **14. Fair scheduling** | ✅ | Round-robin across campaigns, priority-based option |
| **15. Campaign isolation** | ✅ | Per-campaign slots, one campaign can't block another |
| **16. Non-blocking calls** | ✅ | Async callbacks via `CompletableFuture`, worker pool |
| **17. Docker compose** | ✅ | PostgreSQL, Redis, API, UI all containerized |

## Key Features

- **Campaign Management**: Create, start, pause, cancel campaigns with multiple phone numbers
- **Business Hours**: Configure allowed calling hours per campaign with timezone support
- **Concurrency Control**: Per-campaign concurrency limits with Redis-based slot management
- **Worker Pool**: Fixed thread pool (20 workers) with Redis job queue
- **Retry Handling**: 
  - Exponential backoff for sync failures (HTTP calls to telephony)
  - Fixed delay for callback-triggered retries (failed calls)
  - Configurable callback timeout for lost callbacks
- **Fair Scheduling**: Retries prioritized, round-robin across campaigns
- **Fault Tolerance**: Circuit breaker, rate limiting, and timeout handling
- **Metrics**: Real-time campaign statistics + global worker utilization

## Tech Stack & Rationale

| Component | Technology | Why? |
|-----------|------------|------|
| **Language** | Java 17 | Industry standard for enterprise microservices, strong typing, excellent concurrency support |
| **Framework** | Spring Boot 3.2 | Production-ready, extensive ecosystem, built-in dependency injection, easy configuration |
| **Database** | PostgreSQL | ACID compliance for campaign/call data integrity, excellent for relational data with complex queries, battle-tested at scale |
| **Queue/Cache** | Redis | **Why not Kafka?** Redis is simpler for this use case - we need a lightweight job queue, not event streaming. Redis BLPOP provides blocking queue semantics with minimal overhead. Kafka would be overkill for single-service job distribution and adds operational complexity |
| **Circuit Breaker** | Resilience4j | Native Spring Boot integration, lightweight, supports multiple patterns (circuit breaker, rate limiter, retry) |
| **Rate Limiting** | Bucket4j | Token bucket algorithm, Redis-backed for distributed rate limiting, integrates well with Spring |
| **UI** | React + Vite + Tailwind | Fast development, modern tooling, responsive design out of the box |

### Why PostgreSQL over NoSQL?
- Campaign and call data is inherently relational (campaigns have many calls)
- Need ACID transactions for status updates (prevent double-processing)
- Complex queries for metrics aggregation
- Schema enforcement prevents data corruption

### Why Redis Queue over Kafka?
- **Simplicity**: Single service doesn't need distributed event streaming
- **Latency**: Redis BLPOP has sub-millisecond latency vs Kafka's batching delays
- **Operational cost**: No ZooKeeper, no partition management, no consumer groups
- **Use case fit**: We need a simple job queue, not event sourcing or replay capability
- **When to use Kafka**: If we needed multi-consumer event streaming, audit logs, or cross-service communication

## Setup Instructions

### Prerequisites

- **Java 17** (required - Lombok has compatibility issues with Java 21+)
- Maven 3.8+
- Docker & Docker Compose (for Redis/PostgreSQL)

> **Note:** If you have multiple Java versions installed, set JAVA_HOME before running:
> ```bash
> export JAVA_HOME=/path/to/java17
> ```

### Option 1: Docker (Recommended)

```bash
# Start all services
docker-compose up --build

# UI: http://localhost:3000
# API: http://localhost:8081
# H2 Console: http://localhost:8081/h2-console (when using H2)
```

To stop:
```bash
docker-compose down
```

### Option 2: Local Development (requires Java 17)

```bash
# Start Redis and PostgreSQL
docker-compose up postgres redis -d

# Build and run
export JAVA_HOME=/path/to/java17
mvn clean package -DskipTests
mvn spring-boot:run -Dspring-boot.run.profiles=docker
```

### Running Tests

```bash
mvn test
```

## API Reference

### Campaign APIs

#### Create Campaign
```bash
curl -X POST http://localhost:8081/api/v1/campaigns \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Marketing Campaign Q1",
    "description": "Outreach for Q1 promotions",
    "phoneNumbers": ["+1234567890", "+0987654321", "+1122334455"],
    "concurrencyLimit": 5,
    "priority": 7,
    "retryConfig": {
      "maxRetries": 3,
      "syncInitialBackoffMs": 1000,
      "syncBackoffMultiplier": 2.0,
      "callbackRetryDelayMs": 30000,
      "callbackTimeoutMs": 120000
    },
    "businessHours": {
      "startTime": "09:00",
      "endTime": "18:00",
      "timezone": "America/New_York",
      "allowedDays": "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY"
    }
  }'
```

#### Get Campaign
```bash
curl http://localhost:8081/api/v1/campaigns/{campaignId}
```

#### List All Campaigns
```bash
curl http://localhost:8081/api/v1/campaigns
```

#### Start Campaign
```bash
curl -X POST http://localhost:8081/api/v1/campaigns/{campaignId}/start
```

#### Pause Campaign
```bash
curl -X POST http://localhost:8081/api/v1/campaigns/{campaignId}/pause
```

#### Cancel Campaign
```bash
curl -X POST http://localhost:8081/api/v1/campaigns/{campaignId}/cancel
```

#### Get Campaign Calls
```bash
curl http://localhost:8081/api/v1/campaigns/{campaignId}/calls
```

### Call APIs

#### Trigger Single Call
```bash
curl -X POST http://localhost:8081/api/v1/calls \
  -H "Content-Type: application/json" \
  -d '{"phoneNumber": "+1234567890"}'
```

#### Get Call Status
```bash
curl http://localhost:8081/api/v1/calls/{callId}
```

#### Callback Endpoint (for telephony provider)
```bash
curl -X POST http://localhost:8081/api/v1/calls/callback \
  -H "Content-Type: application/json" \
  -d '{
    "externalCallId": "mock-uuid-here",
    "status": "COMPLETED",
    "durationSeconds": 45
  }'
```

## Configuration

Key configuration options in `application.yml`:

```yaml
voice-campaign:
  scheduler:
    enabled: true              # Enable/disable call scheduler
    fixed-rate-ms: 1000        # Scheduler polling interval
    batch-size: 100            # Max calls to enqueue per iteration
  
  worker:
    pool-size: 20              # Number of worker threads
    queue-poll-timeout-ms: 1000  # Worker poll timeout
    max-queue-depth: 1000      # Max jobs in Redis queue
  
  defaults:
    concurrency-limit: 10      # Default concurrent calls per campaign
    max-retries: 3             # Default max retry attempts
    callback-timeout-ms: 120000 # Default callback timeout (2 min)
  
  telephony:
    mock-enabled: true         # Use mock telephony service
    mock-callback-failure-rate: 0.05  # 5% callback failures
    mock-no-callback-rate: 0.01       # 1% no callback (timeout)
    mock-sync-failure-rate: 0.005     # 0.5% sync failures
```

## Scheduling Strategies

The system supports pluggable scheduling strategies:

1. **RoundRobinSchedulingStrategy** (default): Fair distribution across campaigns
2. **PrioritySchedulingStrategy**: Higher priority campaigns get more slots
3. **RemainingCallsSchedulingStrategy**: Prioritizes campaigns closer to completion

## Retry Behavior

| Failure Type | Backoff Strategy | Configuration |
|--------------|------------------|---------------|
| Sync HTTP failure | Exponential | `syncInitialBackoffMs`, `syncBackoffMultiplier` |
| Callback failure | Fixed delay | `callbackRetryDelayMs` |
| Lost callback | Treated as failure | `callbackTimeoutMs` (configurable per campaign) |

## Fault Tolerance

- **Circuit Breaker**: Opens after 50% failure rate (configurable)
- **Rate Limiting**: 100 requests/second per client IP
- **Callback Watchdog**: Detects and handles lost callbacks every 30s

## Database Schema

### campaigns
- `id` (UUID, PK)
- `name`, `description`
- `status` (PENDING, IN_PROGRESS, PAUSED, COMPLETED, FAILED, CANCELLED)
- `concurrency_limit`, `priority`
- `retry_config` (embedded)
- `business_hours` (embedded)

### call_requests
- `id` (UUID, PK)
- `campaign_id` (FK)
- `phone_number`
- `status` (PENDING, SCHEDULED, IN_PROGRESS, COMPLETED, FAILED, PERMANENTLY_FAILED, CANCELLED)
- `retry_count`, `next_retry_at`, `expected_callback_by`
- `external_call_id`, `failure_reason`, `call_duration_seconds`

## Monitoring & Debugging

| Endpoint | URL | Purpose |
|----------|-----|---------|
| **Health Check** | http://localhost:8081/actuator/health | Service health status |
| **Metrics** | http://localhost:8081/actuator/metrics | JVM and app metrics |
| **Info** | http://localhost:8081/actuator/info | Application info |
| **H2 Console** | http://localhost:8081/h2-console | In-memory DB browser (dev mode only, not available in Docker with PostgreSQL) |

### H2 Console (Local Dev Only)
When running locally with H2 (not Docker), access the H2 console:
- URL: `http://localhost:8081/h2-console`
- JDBC URL: `jdbc:h2:mem:voicecampaign`
- Username: `sa`
- Password: (empty)

> **Note**: H2 is only used for local development. Docker uses PostgreSQL.

### Actuator Endpoints
Spring Actuator provides operational endpoints:
```bash
# Health check
curl http://localhost:8081/actuator/health

# Application metrics
curl http://localhost:8081/actuator/metrics

# Specific metric (e.g., JVM memory)
curl http://localhost:8081/actuator/metrics/jvm.memory.used
```

## Useful Commands

### Database (PostgreSQL)

```bash
# Connect to PostgreSQL
docker exec -it voice-campaign-postgres psql -U postgres -d voicecampaign

# Clear all data (campaigns and calls)
docker exec voice-campaign-postgres psql -U postgres -d voicecampaign -c "TRUNCATE TABLE call_requests CASCADE; TRUNCATE TABLE campaigns CASCADE;"

# View campaign counts by status
docker exec voice-campaign-postgres psql -U postgres -d voicecampaign -c "SELECT status, COUNT(*) FROM campaigns GROUP BY status;"

# View call counts by status
docker exec voice-campaign-postgres psql -U postgres -d voicecampaign -c "SELECT status, COUNT(*) FROM call_requests GROUP BY status;"

# View failure reasons
docker exec voice-campaign-postgres psql -U postgres -d voicecampaign -c "SELECT failure_reason, COUNT(*) FROM call_requests WHERE status = 'FAILED' OR status = 'PERMANENTLY_FAILED' GROUP BY failure_reason;"

# Check table sizes
docker exec voice-campaign-postgres psql -U postgres -d voicecampaign -c "SELECT relname, n_live_tup FROM pg_stat_user_tables;"
```

### Redis

```bash
# Connect to Redis CLI
docker exec -it voice-campaign-redis redis-cli

# Clear all Redis data
docker exec voice-campaign-redis redis-cli FLUSHALL

# View all keys
docker exec voice-campaign-redis redis-cli KEYS "*"

# Check queue depth
docker exec voice-campaign-redis redis-cli LLEN "call:queue"

# View active slots for a campaign
docker exec voice-campaign-redis redis-cli GET "campaign:<campaign-id>:active_slots"

# Monitor Redis commands in real-time
docker exec voice-campaign-redis redis-cli MONITOR
```

### Docker

```bash
# Start all services
docker-compose up --build -d

# Stop all services
docker-compose down

# Stop and remove volumes (full reset)
docker-compose down -v

# View logs
docker logs voice-campaign-api -f
docker logs voice-campaign-postgres -f
docker logs voice-campaign-redis -f

# Restart API only
docker-compose restart api

# Rebuild and restart API
docker-compose build api && docker-compose up -d api
```

### Full Reset (Clear Everything)

```bash
# Clear Redis + PostgreSQL data
docker exec voice-campaign-redis redis-cli FLUSHALL && \
docker exec voice-campaign-postgres psql -U postgres -d voicecampaign -c "TRUNCATE TABLE call_requests CASCADE; TRUNCATE TABLE campaigns CASCADE;"
```

## License

MIT
