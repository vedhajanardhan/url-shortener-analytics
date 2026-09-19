# URL Shortener with Click Analytics — Deployment-Ready

A Bitly-style URL shortener: JWT-authenticated URL CRUD, async click tracking,
QR codes, Redis caching with MySQL fallback, and a live analytics dashboard.
Spring Boot + MySQL + Redis + Kafka, hardened for production deployment.

> **Note on this revision**: this codebase was hardened from an earlier working
> prototype (auth, ownership, validation, fault tolerance, reliability, tests,
> docs, and CI were added/fixed on top of the existing architecture — nothing
> was rebuilt from scratch). See "What changed and why" below for the specific
> fixes.

## Architecture

```
                     ┌──────────────┐
   POST /api/urls ──▶│ UrlController│──▶ MySQL (url_mapping, owned by JWT user)
   (JWT required)    └──────────────┘         + Redis (cache seed, TTL-capped
                                                 to the URL's own expiry)

   GET /{shortCode} ─▶ RedirectController (public)
                          │
                          ▼
                    UrlShortenerService
                          │
              ┌───────────┴────────────┐
              ▼                        ▼
       Redis cache-aside        Kafka producer (idempotent,
       (falls back to MySQL      acks=all, fire-and-forget)
        if Redis is down)               │
              │                        ▼
              ▼                Kafka topic: click-events
        302 redirect                   │
        (fast, sync)                   ▼
                              ClickEventConsumer
                              (manual ack - offset only
                               advances after MySQL write)
                                        │
                          ┌─────────────┼──────────────┐
                          ▼             ▼              ▼
                   UA parsing    Geo lookup      MySQL (click_event)
                   (uap-java)    (stub, swap          │
                                  in MaxMind)          │
                                                        ▼
                                          Failed after retries -> DLQ topic
                                          (click-events-dlq), never dropped

   GET /api/analytics/{code} ◀── owner-or-admin only
   (Redis live count, falls back to MySQL COUNT(*) if Redis is down;
    scheduled job reconciles drift between the two every 5 min)
```

## Stack

| Concern | Choice |
|---|---|
| API | Spring Boot 3 / Java 17 |
| Auth | Spring Security + JWT (jjwt), BCrypt password hashing |
| Persistence | MySQL 8 (Flyway-migrated schema, incl. users + ownership) |
| Cache | Redis — cache-aside, live click counters, rate limiting — all with fallback |
| Async pipeline | Kafka — idempotent producer, DLQ + retry backoff on the consumer |
| QR codes | ZXing, PNG output cached in Redis (best-effort) |
| API docs | springdoc-openapi (Swagger UI) |
| Dashboard | Static HTML + Chart.js, JWT-aware (login/register built in) |
| Tests | JUnit 5, Mockito, Testcontainers (MySQL + Kafka), Awaitility |
| CI | GitHub Actions — test, build, Docker build-verify |

## What changed and why (fixes applied to the existing codebase)

- **Auth was entirely missing** → added JWT register/login (`AuthController`/
  `AuthService`/`JwtService`), stateless Spring Security filter chain.
- **No ownership** → `UrlMapping.owner` (nullable FK, so pre-existing rows stay
  valid), ownership enforced on update/delete/analytics with an admin override.
- **No real URL validation** → `UrlValidationService` blocks non-http(s)
  schemes (`javascript:`, `data:`), missing hosts, and self-referential
  redirect loops.
- **Custom alias / redirect route length mismatch (real bug)** → an alias
  longer than the redirect route's regex bound would save successfully then
  be permanently unreachable. Both are now capped at the same 3–30 chars.
- **Redis had no failure handling** → cache, counters, rate limiter, and QR
  cache are all wrapped; a Redis outage degrades to "hit MySQL every time"
  instead of 500ing. Rate limiting fails *open* (never blocks all traffic).
- **Cached entries could outlive their own expiry** → cache TTL is now capped
  to `min(configured TTL, time until the URL's expiresAt)`.
- **Kafka consumer swallowed all exceptions** → replaced with manual
  acknowledgment + `DefaultErrorHandler` (exponential backoff, then a
  dead-letter topic) so a transient DB blip retries instead of silently
  losing the click.
- **Placeholder short code could collide under concurrency (real bug)** → a
  fixed `"PENDING"` placeholder on the unique `short_code` column would
  collide under concurrent requests; replaced with a per-request UUID.
- **Analytics could silently show 0 during a Redis outage** → `getTotal()`
  returns a sentinel (`UNAVAILABLE`, not `0`); the analytics endpoint falls
  back to `COUNT(*)` on MySQL and reports which source it used
  (`totalClicksSource`). A scheduled job also reconciles drift every 5 min.
- **Generic error responses leaked exception internals** → unexpected errors
  now log full detail server-side with a correlation ID, and return only a
  generic message + that ID to the client.
- **No tests beyond one unit test class** → added unit tests for validation,
  JWT, auth, and ownership logic; a dedicated Redis-outage test using a real
  (unreachable) Redis connection, not a mock; and a Testcontainers-backed
  integration test covering register → create → redirect → async click
  tracking → ownership-enforced analytics, end-to-end.
- **`embedded-redis` test dependency** is unmaintained since 2019 and its
  bundled native binaries frequently fail on modern JDKs — replaced with
  Testcontainers (MySQL, Kafka) plus a real-connection-based Redis fallback
  test that needs no container at all.
- **No Swagger, no CI, no env-var config, no structured logging, no
  Docker healthchecks/non-root user** → all added (see below).

## Running locally

```bash
cp .env.example .env   # then edit JWT_SECRET etc.
docker-compose up --build
```

Starts MySQL, Redis, Zookeeper, Kafka, Kafka-UI (http://localhost:8081), and
the app on port 8080.

- Dashboard: **http://localhost:8080/dashboard/index.html**
- Swagger UI: **http://localhost:8080/swagger-ui.html**
- Health check: **http://localhost:8080/actuator/health**

## Running tests

```bash
mvn test
```

Unit tests run with no external dependencies. Integration tests
(`AuthAndUrlFlowIntegrationTest`) spin up real MySQL and Kafka via
Testcontainers — **Docker must be running** locally (or use GitHub Actions,
which has Docker preinstalled).

> I could not execute a live Maven build inside the sandbox used to prepare
> this change (no Maven Central network access there), so every edit was
> reviewed manually for correctness. Please run `mvn test` yourself before
> deploying, and rely on the GitHub Actions workflow below as the real
> build/test gate on every push.

## API overview (full detail in Swagger UI)

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/api/auth/register` | none | Create an account, get a JWT |
| POST | `/api/auth/login` | none | Log in, get a JWT |
| POST | `/api/urls` | JWT | Shorten a URL (owned by you) |
| GET | `/api/urls` | JWT | List your URLs |
| PUT | `/api/urls/{shortCode}` | JWT, owner | Update destination/expiry |
| DELETE | `/api/urls/{shortCode}` | JWT, owner | Deactivate a URL |
| GET | `/api/urls/{shortCode}/qr` | none | QR code PNG |
| GET | `/{shortCode}` | none | Redirect + track click |
| GET | `/api/analytics/{shortCode}` | JWT, owner | Click analytics |

## Deploying to Render

Render doesn't offer managed MySQL or Kafka, so the plan is: **Render** hosts
the Spring Boot app + a managed Redis instance; **MySQL and Kafka are external
managed services** (both have workable free/cheap tiers). This keeps your
existing Spring Boot + MySQL + Redis + Kafka architecture unchanged.

### 1. Provision external MySQL

Any managed MySQL works — **Aiven for MySQL** (free tier) or **PlanetScale**
are the easiest. Create a database, then note:
- Host, port, database name, username, password
- Whether it requires SSL (Aiven does)

### 2. Provision external Kafka

**Upstash Kafka** (serverless, generous free tier, simplest setup) is
recommended. Create a topic-less cluster (topics are auto-created by the app
via `NewTopic` beans), then note:
- Bootstrap endpoint
- Username/password (SASL) and that it uses `SASL_SSL`

### 3. Create a Render Key Value (Redis) instance

In the Render dashboard: **New → Key Value**. Note the internal connection
URL Render gives you (host, port, password).

### 4. Create the Render Web Service

**New → Web Service** → connect your GitHub repo → Render detects the
`Dockerfile` automatically (no build command needed — it builds the image
from the Dockerfile).

- **Health check path**: `/actuator/health`
- **Instance type**: at least Starter (512MB) — set `JAVA_OPTS=-Xmx400m` to
  stay safely under that

### 5. Environment variables to set in Render

| Variable | Value |
|---|---|
| `PORT` | Render sets this automatically — don't override |
| `DB_HOST` | your Aiven/PlanetScale host |
| `DB_PORT` | usually `3306` (check your provider) |
| `DB_NAME` | your database name |
| `DB_USER` | your database username |
| `DB_PASSWORD` | your database password |
| `DB_USE_SSL` | `true` (required by most managed MySQL) |
| `REDIS_HOST` | Render Key Value internal host |
| `REDIS_PORT` | Render Key Value port |
| `REDIS_PASSWORD` | Render Key Value password |
| `REDIS_SSL_ENABLED` | `true` if your Render Key Value plan requires TLS |
| `KAFKA_BROKERS` | Upstash bootstrap endpoint |
| `KAFKA_SECURITY_PROTOCOL` | `SASL_SSL` |
| `KAFKA_SASL_MECHANISM` | `SCRAM-SHA-256` (check Upstash's current docs) |
| `KAFKA_SASL_JAAS_CONFIG` | `org.apache.kafka.common.security.scram.ScramLoginModule required username="..." password="...";` |
| `JWT_SECRET` | generate with `openssl rand -base64 48` — required, ≥32 chars |
| `JWT_EXPIRATION_MS` | `86400000` (24h) or your preference |
| `APP_BASE_URL` | `https://<your-app>.onrender.com` |
| `CORS_ALLOWED_ORIGINS` | your frontend origin, or `APP_BASE_URL` if dashboard-only |
| `RATE_LIMIT_PER_MINUTE` | `20` (or your preference) |
| `CACHE_TTL_HOURS` | `24` |
| `ANALYTICS_RECONCILE_INTERVAL_MS` | `300000` |
| `SPRING_PROFILES_ACTIVE` | `prod` (structured logging) |
| `JAVA_OPTS` | `-Xmx400m` (tune to your instance size) |

### 6. Deploy

Push to your connected branch — Render builds the Dockerfile and deploys
automatically. Flyway runs migrations against your external MySQL on startup.

### 7. Verify

```bash
curl https://<your-app>.onrender.com/actuator/health
curl -X POST https://<your-app>.onrender.com/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"test","email":"test@example.com","password":"SecurePass123"}'
```

## CI/CD

`.github/workflows/ci.yml` runs on every push/PR to `main`: `mvn test`
(including the Testcontainers integration suite — GitHub-hosted runners have
Docker preinstalled), then `mvn package`, then a Docker build to verify the
Dockerfile stays buildable. Render's own GitHub integration handles deploy-on-
push separately — no extra Action needed for that.

## What's still stubbed / worth knowing about

- `GeoLookupService` returns "Unknown" for country — swap in MaxMind GeoLite2
  (offline db) or an IP-geo API for real geographic breakdowns.
- No email verification on registration — add if this becomes public-facing.
- `click_event` has no retention/archival policy — will grow unbounded at
  scale; partition by month or archive old rows.
- Rate limiting is per-IP and fixed-window — fine for a portfolio project,
  but a determined abuser behind a shared IP (e.g. NAT) could still hit
  limits meant for others; consider per-user limits once auth is universal.
