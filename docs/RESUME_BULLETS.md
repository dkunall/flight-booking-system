# Resume bullets: Flight Booking System (personal project)

**Flight Booking System** | Java 17, Spring Boot 3, Spring Data JPA, PostgreSQL, Flyway, Spring Security (JWT), Redis, JUnit 5, Mockito, Docker, GitHub Actions

- Built a Cleartrip-style flight booking backend in Java 17 and Spring Boot 3 with 15 REST endpoints covering flight search (route/date filters, price and airline filters, sorting by price/duration/departure, pagination), seat holds, booking, payment and cancellation, secured with JWT and USER/ADMIN roles.
- Prevented double booking with `PESSIMISTIC_WRITE` row locks taken in primary-key order (deadlock-free), a `@Version` safety net, 10-minute seat holds and a `@Scheduled` job that releases expired holds. A concurrency integration test sends 12 parallel threads at the same seat and verifies that exactly one succeeds.
- Modelled the booking lifecycle as a guarded, table-driven state machine (INITIATED → SEATS_HELD → PAYMENT_PENDING → CONFIRMED/FAILED/CANCELLED → REFUNDED). Used Strategy for composable fares (base fare, occupancy-based pricing, coupons), Factory for mock UPI/card gateways, and Observer (Spring events, after-commit, async) for email/SMS notifications.
- Made booking-create and payment endpoints idempotent with an `Idempotency-Key` header backed by a uniquely constrained table, so retries replay the stored response instead of creating a second booking or charge.
- Designed a 10-table PostgreSQL schema with Flyway migrations, CHECK/unique constraints and query-driven indexes. Added a Spring Cache layer (in-memory by default, Redis via docker-compose), correlation-ID logging, a global JSON error handler and Swagger docs. Covered the code with 91 JUnit 5/Mockito unit and Spring Boot integration tests running in GitHub Actions CI.
