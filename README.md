# flight-booking-system

Backend for a Cleartrip-style flight booking flow: search, seat holds, booking, payment, cancel and refund.
Java 21, Spring Boot 3.5, Postgres 16, Flyway, Redis for the search cache (in Docker), JWT auth.

I built it to work through the parts of booking systems that tend to go wrong: two people grabbing the same
seat, a client retrying a payment, and a gateway sending the same callback twice.

## What it does

- **Search** `GET /api/flights/search` for direct flights (filters, sort, paging), and `GET /api/flights/routes`
  for itineraries with up to 2 stops. Connections must allow 45 min to 6 h; results are sorted by price or
  duration. Both are cached.
- **Hold** `POST /api/bookings` locks the seats for 10 minutes. The seat rows are taken with `SELECT ... FOR UPDATE`,
  so only one hold can win. A scheduler releases expired holds.
- **Pay** `POST /api/bookings/{id}/payments` with UPI, card or netbanking. The gateways are mocks. Netbanking stays
  pending until `POST /api/payments/callback` arrives with a valid HMAC-SHA256 signature. Duplicate callbacks are no-ops.
- **Cancel** `POST /api/bookings/{id}/cancel`. A confirmed booking gets refunded and its seats go back on sale.
- **Idempotency-Key** on hold and pay. A replay returns the original response with `Idempotent-Replayed: true`.
- Booking status changes go through a state machine. Each change writes an outbox row in the same transaction.
  A poller then publishes it to the (log-only) email/SMS listeners.
- Errors are RFC 7807 `application/problem+json` with a stable `code`.
- `@Version` on bookings and schedules, pagination on `GET /api/bookings`, and a USER/ADMIN split
  (`/api/admin/**` handles inventory and a route revenue report).
- Public search is rate limited per client IP with a token bucket (30 burst, 10/s). Over the limit you get a 429 with `Retry-After`.
- Actuator health/readiness/liveness plus Prometheus on a separate port. JSON (ECS) logs with a correlation id,
  and graceful shutdown.

Swagger UI is at `/swagger-ui.html` when the app is running.

## Running it

You need Docker. Then:

```bash
cp .env.example .env    # fill in the secrets, e.g. openssl rand -base64 48
docker compose up -d --build
./scripts/demo.sh       # walks through search -> hold -> pay -> cancel/refund, callbacks and the rate limit
```

| what | where |
|---|---|
| API | http://127.0.0.1:7580 |
| actuator / prometheus | http://127.0.0.1:7581/actuator/prometheus |
| Postgres | 127.0.0.1:55480 |
| Redis | 127.0.0.1:6430 |
| Prometheus (optional, `--profile metrics`) | http://127.0.0.1:9120 |

The `dev` profile seeds 6 airports, 3 airlines and 11 flights with 7 days of schedules, so search works right away.
`JWT_SECRET`, `PAYMENT_WEBHOOK_SECRET` and `POSTGRES_PASSWORD` have no defaults outside dev. Compose refuses to
start without them.

To run without Docker you need a local Postgres (the in-memory cache is used unless the `docker` profile is on): `./mvnw spring-boot:run -Dspring-boot.run.profiles=dev`.

## Tests

```bash
./mvnw verify
```

116 tests: 76 unit and 40 integration. The integration tests run against a real Postgres 16 through
Testcontainers, so you need Docker for those. They include:

- a test with 20 threads racing for the same seat, asserting exactly one winner
- idempotent replays
- the signed callback flow
- hold expiry
- the rate limiter

ArchUnit checks the layering: controllers don't touch repositories, and the domain doesn't depend on web/service code.

CI (GitHub Actions) runs `mvn verify`, builds the Docker image and scans it with Trivy. It fails on any
fixable HIGH/CRITICAL finding.

## Numbers

These are from my dev box, not a benchmark. k6 and the app ran on the same shared 8-vCPU machine, with the app
and Postgres in Docker and other stuff running alongside. Scripts are in `scripts/perf/`.

- search, 20 VUs for 30 s: ~2,000 req/s, p95 20 ms, no errors. Mostly cache hits, since it only rotates over 30 keys.
- 2-stop route search, same setup: ~2,200 req/s, p95 18 ms (also cached).
- hold + pay: 1,000 bookings across 20 VUs in 4.1 s (~240/s). p95 was 73 ms for hold and 68 ms for pay, and all 1,000 confirmed.
- 50 concurrent holds on one seat (`seat-race.py`): 1 × 201 and 49 × 409, in 223 ms.

Cold search against Postgres was not measured separately.

## What's missing

- **Payments are mocks.** No real gateway, single currency (INR), and no partial refunds or cancellation fees.
- **Multi-stop routes are search only.** To book one you book each leg separately. There is no single PNR across legs.
- **The rate limiter lives in process memory**, so with N replicas each one has its own bucket. It also keys on the
  remote address. Behind a proxy you have to set `FORWARD_HEADERS_STRATEGY=native`, or every client ends up sharing one bucket.
  A real deployment would do this at the gateway or in Redis.
- **Outbox consumers just log.** No email/SMS provider, and the poller isn't sharded for multiple instances.
- **Redis has no Testcontainers test.** The ITs use the in-memory cache.
- **spring-webmvc 6.2.19 has two open CVEs** that are only fixed in Spring 7 / Boot 4. They're listed in `.trivyignore`
  until I do that upgrade.
- **Some package cycles remain** (config ↔ service/security, domain ↔ exception). ArchUnit checks layers but not cycles yet.
- No seat-map UI, and no load testing beyond the single-box runs above.

## Layout

```
controller/   REST endpoints
service/      booking, search, route finder, payments, holds, idempotency, outbox
domain/       JPA entities and enums (the state machine lives on Booking)
pattern/      fare strategies, payment gateway factory, booking state, notification observers
security/     JWT filter, rate limiter
config/       security, cache, metrics, filters, dev seed data
db/migration  Flyway scripts
```
