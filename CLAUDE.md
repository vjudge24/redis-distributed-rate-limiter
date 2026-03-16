# CLAUDE.md — Redis Distributed Rate Limiter

## Project Overview

A high-performance distributed rate limiting system built with **Java 17 + Spring Boot 3.2.1 + Redis + MySQL**. It supports 4 rate limiting algorithms via Spring AOP and atomic Lua scripts, with runtime dynamic configuration through a MySQL-backed REST API and gRPC-based multi-instance synchronization.

## Build & Run

```bash
# Prerequisites: JDK 17+, Maven 3.6+, Redis 5.0+, MySQL 5.7+

# Compile (skip tests — no automated test suite exists)
mvn clean package -DskipTests

# Run
mvn spring-boot:run

# Application runs on port 8082
# Management UI: http://localhost:8082/index.html
# Health check: http://localhost:8082/actuator/health
# gRPC config center: port 9090
```

### External Dependencies

- **Redis** on `localhost:6379` — rate limiting state storage
- **MySQL** on `localhost:3306/rate_limiter` — config persistence (credentials in `application.yml`)
- Schema auto-created on startup from `src/main/resources/schema.sql`

## Project Structure

```
src/main/java/com/example/ratelimiter/
├── RateLimiterApplication.java          # Spring Boot entry point
├── annotation/
│   └── RateLimit.java                   # @RateLimit annotation (SpEL, 4 algorithms, sharding)
├── aspect/
│   └── RateLimitAspect.java             # AOP interceptor: SpEL parsing, DB config priority, shard dispatch
├── config/
│   ├── RedisConfig.java                 # Lettuce connection pool setup
│   └── GlobalExceptionHandler.java      # @ControllerAdvice for RateLimitException → 429
├── controller/
│   ├── TestController.java              # Demo endpoints for all 4 algorithms
│   ├── RateLimitConfigController.java   # CRUD REST API for runtime config
│   └── Main.java
├── dto/
│   ├── RateLimitConfigRequest.java      # Request DTO with Bean Validation
│   └── RateLimitConfigResponse.java     # Response DTO
├── entity/
│   └── RateLimitConfig.java             # JPA entity → rate_limit_config table
├── exception/
│   └── RateLimitException.java          # Custom exception (triggers HTTP 429)
├── grpc/
│   ├── ConfigCenterGrpcServer.java      # Streaming server: pushes config changes to watchers
│   └── ConfigCenterGrpcClient.java      # Client: subscribes to config change events
├── mapper/
│   └── RateLimitConfigMapper.java       # MyBatis mapper interface
├── model/
│   ├── RateLimitType.java               # Enum: FIXED_WINDOW, SLIDING_WINDOW, TOKEN_BUCKET, LEAKY_BUCKET
│   └── Result.java                      # Generic API response wrapper
└── service/
    ├── RateLimiterService.java          # Rate limiter interface
    ├── RateLimiterServiceImpl.java      # 4 Lua script implementations + shard logic
    └── RateLimitConfigService.java      # Config CRUD, ConcurrentHashMap cache, gRPC notifications

src/main/resources/
├── application.yml                      # Redis, MySQL, MyBatis, gRPC config
├── schema.sql                           # DDL for rate_limit_config table
├── mapper/
│   └── RateLimitConfigMapper.xml        # MyBatis SQL mappings
└── static/
    └── index.html                       # Single-page management UI

src/main/proto/
└── rate_limit_config.proto              # gRPC service definition (Watch + FetchAllConfigs)
```

## Architecture

### Request Flow

1. Client calls annotated endpoint → `RateLimitAspect.around()` intercepts
2. Aspect resolves rate limit key (SpEL expressions supported)
3. Checks DB config cache (ConcurrentHashMap) — DB config overrides annotation defaults
4. Calls `RateLimiterServiceImpl` which executes the appropriate Lua script on Redis
5. If limit exceeded → throws `RateLimitException` → `GlobalExceptionHandler` returns HTTP 429

### Configuration Priority

**Database config > @RateLimit annotation defaults.** When DB config is deleted or disabled, the system falls back to annotation values.

### Multi-Instance Sync (gRPC)

- `ConfigCenterGrpcServer` maintains Watch subscriptions and pushes changes via server-streaming
- `ConfigCenterGrpcClient` subscribes on startup, receives push notifications
- On change → client pulls fresh configs via `FetchAllConfigs` RPC
- Fallback: local ConcurrentHashMap cache ensures zero-downtime on sync failures

### Hot Key Sharding (Redis Cluster)

- `@RateLimit(shardCount=N)` distributes a single key across N Redis shards
- Each shard gets `limit/shardCount` quota
- Uses hash tags `{key}` for cluster slot routing
- Reduces hotspot pressure in Redis Cluster deployments

## Four Rate Limiting Algorithms

All implemented as atomic Lua scripts in `RateLimiterServiceImpl`:

| Algorithm | Redis Structure | Use Case | Performance |
|-----------|----------------|----------|-------------|
| **Fixed Window** | String counter + TTL | Simple, low-precision scenarios | 10K+ QPS, 2ms |
| **Sliding Window** | ZSET with timestamps | Most scenarios (recommended) | 8K+ QPS, 3ms |
| **Token Bucket** | Hash (tokens + timestamp) | Burst traffic tolerance | 9K+ QPS, 2.5ms |
| **Leaky Bucket** | Hash (water + timestamp) | Constant rate enforcement | 9K+ QPS, 2.5ms |

## Key API Endpoints

### Test Endpoints
- `GET /api/test/info` — System info
- `GET /api/test/fixed-window` — Fixed window demo (5 req/60s)
- `GET /api/test/sliding-window` — Sliding window demo (10 req/30s)
- `GET /api/test/token-bucket` — Token bucket demo (capacity=10, rate=2/s)
- `GET /api/test/leaky-bucket` — Leaky bucket demo (capacity=5, rate=1/s)
- `GET /api/test/user/{userId}` — Per-user rate limiting

### Config Management
- `GET /api/rate-limit-configs` — List all configs
- `GET /api/rate-limit-configs/{id}` — Get by ID
- `GET /api/rate-limit-configs/by-key?key=xxx` — Get by key
- `POST /api/rate-limit-configs` — Create config
- `PUT /api/rate-limit-configs/{id}` — Update config
- `DELETE /api/rate-limit-configs/{id}` — Delete config
- `POST /api/rate-limit-configs/refresh-cache` — Manual cache refresh

### Actuator
- `/actuator/health`, `/actuator/info`, `/actuator/metrics`

## Development Guide

### Adding Rate Limiting to an Endpoint

```java
@RateLimit(
    key = "api:user:#{#userId}",       // SpEL expression for dynamic keys
    limit = 10,                         // Max requests in window
    period = 60,                        // Window in seconds
    type = RateLimitType.SLIDING_WINDOW,
    message = "Too many requests",
    shardCount = 3                      // Optional: distribute across 3 shards
)
@GetMapping("/user/{userId}/profile")
public Result getUserProfile(@PathVariable Long userId) { ... }
```

### Adding a New Algorithm

1. Define a new Lua script constant in `RateLimiterServiceImpl`
2. Add the algorithm type to `RateLimitType` enum
3. Add a case in the `tryAcquire()` switch statement
4. Create a `tryAcquireNewAlgorithm()` method

### Modifying Rate Limits at Runtime

Use the REST API — changes take effect immediately on the local instance and propagate to other instances via gRPC push.

```bash
curl -X POST http://localhost:8082/api/rate-limit-configs \
  -H "Content-Type: application/json" \
  -d '{"limitKey":"test:fixed-window","type":"FIXED_WINDOW","limitCount":20,"period":60,"enabled":true}'
```

## Code Conventions

- **Language**: Java 17 (text blocks, records where applicable)
- **Framework**: Spring Boot 3.2.1 with Spring AOP
- **ORM**: MyBatis 3.0.3 with XML mappers in `src/main/resources/mapper/`
- **Validation**: Jakarta Bean Validation on DTOs
- **Response format**: All endpoints return `Result<T>` wrapper
- **Exception handling**: `@ControllerAdvice` in `GlobalExceptionHandler`
- **Redis key prefix**: `rate_limit:` (configurable in `application.yml`)
- **Error strategy**: Fail-open — Redis/DB failures allow requests through
- **Lombok**: Used for `@Data`, `@Slf4j`, `@Builder`, etc.
- **Protobuf**: `.proto` files in `src/main/proto/`, generated by `protobuf-maven-plugin`

## Testing

No automated test suite. Manual testing via:

```bash
# Quick smoke test
curl http://localhost:8082/api/test/info

# Load test with Apache Bench
ab -n 1000 -c 10 http://localhost:8082/api/test/sliding-window
```

## Redis Cluster Setup

```bash
# Start a 3-node Redis Cluster via Docker Compose
docker-compose -f docker-compose-cluster.yml up -d
```

Configure cluster nodes in `application.yml` under the cluster profile (ports 6380-6382).

## Useful Redis Commands

```bash
redis-cli KEYS "rate_limit:*"                              # List all rate limit keys
redis-cli ZRANGE rate_limit:api:sliding-window 0 -1 WITHSCORES  # Sliding window entries
redis-cli GET rate_limit:api:test                          # Fixed window counter
redis-cli DEL rate_limit:api:test                          # Reset a counter
redis-cli MONITOR                                          # Watch all Redis commands
```
