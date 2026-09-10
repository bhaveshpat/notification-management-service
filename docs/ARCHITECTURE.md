# Architecture

## Components

- **notification-domain** -- shared Gradle module (not independently deployable):
  JPA entities (`Notification`, `DeliveryTask`, `AuditEvent`), enums, and Spring
  Data repositories used by both services below.
- **notification-api** -- public-facing REST API. Accepts notification
  submissions (with idempotency key), persists the `Notification` plus one
  `DeliveryTask` per (recipient, channel), and serves status lookups. Also
  starts a shared H2 TCP server in the dev profile (see Persistence below).
- **delivery-worker** -- polls `delivery_tasks` for QUEUED-and-due rows,
  processes them (routing, provider call, retry/backoff), and updates status +
  audit history. No message broker: the `delivery_tasks` table itself is the
  work queue (DB-backed queue / transactional-outbox style).

## Data model

- **Notification**: notification id, idempotency key (unique), source system,
  event/correlation id, type, severity, priority, recipients (opaque ids),
  requested channels, created/scheduled/expires timestamps, aggregate status.
- **DeliveryTask**: one row per (recipient, channel). Own status, attempt
  count, last/next attempt timestamps, classified failure reason, provider
  message id. This is the unit the worker claims and processes.
- **AuditEvent**: append-only, per notification (and optionally per delivery
  task): event type, timestamp, short details. No payload content or
  credentials ever stored here (4.9).

## State machines

**DeliveryTask** (per recipient+channel):
```
CREATED -> QUEUED -> SENDING -> SUCCEEDED
                         |
                         v
                FAILED_RETRYABLE --(backoff, re-queue, bounded attempts)--> QUEUED
                         |
                         v (attempts exhausted, or permanent failure)
                  FAILED_TERMINAL

(also: SUPPRESSED, for dedup matches / invalid recipients caught pre-send)
```

**Notification** (aggregate): `RECEIVED -> ROUTED -> IN_PROGRESS -> COMPLETED
/ PARTIALLY_DELIVERED / FAILED`, plus `REJECTED` (validation failure) and
`EXPIRED` (past expiration before completion).

Decision: `Notification.status` is a stored field, updated synchronously
(same transaction) whenever a child `DeliveryTask` transitions, rather than
computed at read time via a join/aggregation query on every status lookup.
Documented trade-off: introduces a second place that must stay consistent
with the DeliveryTask rows, but keeps the status-lookup API (4.2) cheap and
simple. Defensible for this scale; a pure read-time aggregation would be the
alternative if consistency drift became a concern.

## Persistence

Two Spring profiles:

- **dev (default)** -- H2 in-memory. Since `notification-api` and
  `delivery-worker` are separate processes, notification-api starts an H2 TCP
  server (`H2TcpServerConfig`) exposing its in-memory DB; delivery-worker
  connects to it over `jdbc:h2:tcp://localhost:9092/mem:notificationdb`
  instead of getting its own empty database. **Start notification-api first.**
  Resets on restart -- documented assumption, acceptable for a 2-3 day
  prototype assignment.
- **postgres** -- real Postgres via the root `docker-compose.yml`. Both
  services just point at a normal network-reachable Postgres instance, so the
  H2-TCP-sharing concern above doesn't apply here. No code changes needed to
  switch: only `application-postgres.yml` + `--spring.profiles.active=postgres`.
  This profile exists to demonstrate the production evolution path, not
  because it's required to run the assignment.

## Open design questions (next increments)

- [x] Notification + delivery data model (entities, state machine)
- [x] Module/service boundary and how they share data (H2 TCP server / DB-backed queue)
- [x] Persistence choice (H2 in-memory by default, Postgres profile for production path)
- [ ] API contract (submit, status) -- REST endpoints, request/response DTOs, validation
- [ ] Deduplication logic -- check idempotency key on submit, short-circuit + audit DUPLICATE_SUPPRESSED
- [ ] Channel routing policy (requested channel / severity / recipient preference / routing policy -> selected channel)
- [ ] Retry/backoff strategy in the worker (currently a wiring skeleton only -- QUEUED -> SENDING, no provider call, no failure handling yet)
- [ ] Channel provider abstraction (mock/simulated providers per channel)

## Known limitations (to carry into the deliverable writeup)

- Single delivery-worker instance assumed; the claim query is a plain SELECT,
  not a locking claim -- would double-process under multiple worker instances.
- H2 TCP sharing is a dev/assignment convenience with a startup-order
  dependency (api before worker); not a production pattern.

## Decisions log

See `docs/DECISIONS.md`.
