# Interview notes: Flight Booking System

## 1. Key design decisions

| Decision | Choice | Reasoning |
|---|---|---|
| Flight vs schedule | `Flight` = route template + cabin layout; `FlightSchedule` = one dated departure | "6E2134" flies every day. Seats, fares and availability belong to a specific departure, not to the flight number. |
| Seat inventory | A physical `seats` row per schedule, generated on schedule creation | Enables seat selection, per-seat locking and a seat map. It costs about 150-200 rows per departure, which is cheap with an index on `(schedule_id, status, seat_class)`. |
| Concurrency | Pessimistic row lock on seats, plus `@Version` | High contention on hot seats. Losers wait milliseconds and fail cleanly instead of failing at commit and retrying. Locks are per seat and taken in id order, so there are no deadlocks. |
| Hold model | Seat status `HELD` + `booking_id` + `hold_expires_at`, and booking `SEATS_HELD` | A hold is just a state, with no separate locks table. A DB CHECK enforces the seat-ownership invariant. |
| Hold expiry | `@Scheduled` job, plus a check at payment time | The job reclaims inventory. The payment check ensures correctness even if the job lags. Each booking expires in its own transaction. |
| State machine | Immutable `EnumMap<BookingStatus, Set<BookingStatus>>`; `Booking.status` has no setter | One auditable place for rules, exhaustively unit-tested, and impossible to bypass. |
| Pricing | Strategy pipeline ordered by `@Order` | New rules are new beans (Open/Closed). Each rule is testable in isolation. |
| Payments | `PaymentGateway` interface + Factory via Spring list injection | No switch on payment method. Gateways validate input before any state change. Cards are accepted only as tokens, never raw numbers. |
| Notifications | Spring `ApplicationEvent` + `@TransactionalEventListener` + `@Async` | Decoupled from booking logic. Listeners fire only after commit and never slow down checkout. |
| Idempotency | DB table with a unique `(key, user, operation)`; reserve, execute, then complete or release | The unique constraint resolves concurrent duplicates. The request hash detects key reuse with a different body. |
| Schema ownership | Flyway migrations + `ddl-auto=validate` | Reviewable, versioned DDL. Hibernate cannot silently change production tables. |
| Money | `BigDecimal`, `NUMERIC(10,2)` / `NUMERIC(12,2)`, HALF_UP to paise | Never use floating point for currency. |
| Time | Injected `Clock` | Expiry and departure logic is deterministic in tests. |
| Auth | Stateless JWT (HS256), claims carry uid and role | No DB lookup per request. Self-registration is always USER; ADMIN is provisioned out of band. |
| Errors | Typed `BusinessException` (status + stable code) → `@RestControllerAdvice` | Clients branch on `code`, not on message text. 401/403 from the security chain use the same JSON shape. |

## 2. Likely interview questions (short answers)

**Q: How do you guarantee two users can't book the same seat?**
`SELECT ... FOR UPDATE` on the seat rows inside the booking transaction. The second transaction blocks
until the first commits, then sees `HELD` and fails with 409. There is also a DB CHECK on seat state and
`@Version` as a backstop. `SeatHoldConcurrencyIT` fires 12 threads at one seat and asserts that exactly one wins.

**Q: Why not optimistic locking?**
Optimistic locking is great when conflicts are rare. Seat selection on a popular flight is high-contention:
every loser would do the full work, fail at commit and need a retry loop. With pessimistic locking the
critical section is a few ms with no external calls, so waiting is cheaper than retrying. I would pick
optimistic locking for low-contention updates such as editing a user profile.

**Q: How would you avoid deadlocks with multi-seat bookings?**
Always acquire locks in a global order (`ORDER BY id`) and add a lock timeout. Two requests for {5,7} and
{7,5} both lock 5 first.

**Q: What happens if the user never pays?**
The booking stays `SEATS_HELD` until `hold_expires_at`. The scheduler (every 30s) moves it to `FAILED`
with reason `HOLD_EXPIRED` and releases the seats. A payment attempt after expiry gets `410 HOLD_EXPIRED`
even if the job hasn't run yet.

**Q: What if the scheduler and a payment race on the same booking?**
Both lock the booking row (`findByIdForUpdate`) and re-check state under the lock. Whoever is second sees the
new state: the scheduler skips a booking that is no longer `SEATS_HELD`, and payment fails on an invalid transition.

**Q: How does idempotency work? What if two identical requests arrive simultaneously?**
Before doing work we insert `(key, user, operation, request_hash, IN_PROGRESS)` in a separate
transaction. The unique constraint lets only one insert succeed. The loser re-reads the record and gets
either the stored response (replay) or 409 if the first request is still running. Same key with a different
body returns 422. If the operation throws, the record is deleted so the client can retry with the same key.

**Q: Gap in your idempotency design?**
If the business transaction commits but saving the response fails, the key stays `IN_PROGRESS`. Fixes:
store the record inside the same transaction as the business write (an outbox-style approach), or add a TTL
or recovery job. I'd also purge keys after about 24h.

**Q: Why is the payment call inside the DB transaction? Is that OK?**
It's acceptable for a mock that returns instantly. For a real gateway I'd split it: mark `PAYMENT_PENDING`
and commit, call the gateway (or wait for a webhook), then confirm in a new transaction. An outbox and a
reconciliation job would cover crashes in between. The seat lock is not held during that call.

**Q: How does search scale? Why filter price in memory?**
The DB narrows candidates by route and date window using indexes (and by airline when filtered), with fetch
joins to avoid N+1 and one GROUP BY for availability. Fare depends on live occupancy, so it isn't a column
we can filter in SQL. A route has tens of departures per day, so in-memory filter, sort and paginate is
trivial. At larger scale: precompute fares into a search index (Elasticsearch) updated by events, and cache
by (route, date).

**Q: Isn't the cache stale on availability?**
Yes, by design. The cache is a 60s view (Redis TTL, or scheduled eviction for the in-memory cache), and
schedule creation evicts it. The authoritative availability check is the row lock at hold time, which is
the same trade-off real OTAs make ("price/seat changed" messages).

**Q: Explain the state machine. Why not just an enum with if-statements?**
The transitions are data (an `EnumMap`), not scattered conditionals. `Booking.transitionTo()` is the only
way to change status. Adding a state, for example `RESCHEDULED`, means editing one table, and a parameterized
test covers every valid and invalid pair.

**Q: How do you make fares extensible? (Strategy)**
Each rule implements `FareStrategy.apply(currentFare, context)`. `FareCalculator` receives all strategies,
ordered by `@Order`, and folds over them. A festive surcharge is a new `@Component` with no change to
existing classes.

**Q: How would you add NetBanking? (Factory/OCP)**
Add `NETBANKING` to the enum and a `NetBankingGateway implements PaymentGateway` bean. The factory builds
its `EnumMap` from injected beans, so no other code changes.

**Q: Why Spring events for notifications instead of calling EmailService directly?**
It decouples the core booking flow from side effects. `@TransactionalEventListener` guarantees no
notification goes out for a rolled-back booking, and `@Async` keeps a slow SMTP or SMS provider off the
request path. In production this event would go to Kafka for retries and durability.

**Q: Walk me through the schema constraints.**
CHECKs on enum columns, positive fares, `arrival > departure`, and the seat invariant (AVAILABLE has no
owner, HELD has an owner and expiry, BOOKED has an owner and no expiry). Uniques on `(schedule_id, seat_number)`,
`(flight_id, departure_time)`, `pnr` and the idempotency scope. Indexes match the queries: search, the
availability GROUP BY, the expiry job, and "my bookings".

**Q: Write the SQL for revenue per route.** (It's in `BookingRepository.routeRevenueReport`.)
```sql
SELECT src.code, dst.code, COUNT(DISTINCT b.id) bookings, COUNT(p.id) passengers, SUM(p.fare) revenue
FROM bookings b
JOIN flight_schedules fs ON fs.id = b.schedule_id
JOIN flights f ON f.id = fs.flight_id
JOIN airports src ON src.id = f.source_airport_id
JOIN airports dst ON dst.id = f.destination_airport_id
JOIN passengers p ON p.booking_id = b.id
WHERE b.status = 'CONFIRMED'
GROUP BY src.code, dst.code
ORDER BY revenue DESC;
```
`COUNT(DISTINCT b.id)` is needed because the passengers join multiplies booking rows.

**Q: How do you prevent N+1 queries?**
Fetch joins in the search and detail queries, aggregated counts in one GROUP BY, and `open-in-view=false`
so lazy loading outside a transaction fails loudly instead of silently issuing queries.

**Q: How is the JWT validated, and what are its weaknesses?**
HS256 signature and expiry are checked in `JwtAuthenticationFilter`, and the principal is built from claims.
Weaknesses: tokens can't be revoked before they expire, and a role change only takes effect on the next
token. Fixes: short TTL plus refresh tokens, or a deny-list in Redis.

**Q: Why return 404, not 403, for someone else's booking?**
It avoids confirming that a booking id exists (enumeration).

**Q: How do you trace a request across logs?**
`CorrelationIdFilter` accepts or generates `X-Correlation-Id`, puts it in the MDC (log pattern
`[cid=...]`), echoes it in the response and error body, and a `TaskDecorator` copies the MDC to async
listener threads.

**Q: How did you test it?**
Unit tests cover pure logic (the state machine, every fare tier, the gateways) and Mockito tests cover services
(including the refund path and expiry). `@SpringBootTest` + MockMvc integration tests run against H2 in
PostgreSQL mode with the real Flyway migrations, covering search, hold, idempotency, pay, decline, cancel/refund,
security, admin and the scheduler. The concurrency test uses an `ExecutorService` and a `CountDownLatch` start
gate. That is 91 tests, run by `mvn verify` in GitHub Actions.

**Q: What would you change for production scale?**
Use Testcontainers with real Postgres in CI, an outbox and Kafka for events and payment callbacks, read
replicas or Elasticsearch for search, Redis-based short-lived seat holds in front of the DB, rate limiting
on search, refresh tokens, purging of idempotency keys, and partitioning seats/bookings by departure date.
