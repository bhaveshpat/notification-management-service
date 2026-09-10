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
- [x] API contract (submit, status) -- POST /notifications, GET /notifications/{id}
- [x] Deduplication logic -- idempotency key checked on submit, short-circuits + audits DUPLICATE_SUPPRESSED
- [x] Channel routing policy -- ChannelRouter interface + DefaultChannelRouter (severity override; see below)
- [x] Retry/backoff strategy in the worker (bounded exponential backoff, failure-reason classification)
- [x] Channel provider abstraction (ChannelProvider interface + SimulatedChannelProvider)
- [x] Aggregate Notification.status rollup from DeliveryTask outcomes (NotificationStatusRollupService, called after every transition)
- [ ] "Reprocessing a queued delivery must not create uncontrolled duplicate side effects" (4.4, worker-side half of dedup) -- see Known limitations

## Delivery processing (worker)

`DeliveryTaskPoller` polls for tasks in QUEUED or FAILED_RETRYABLE status that
are due (`nextAttemptAt` unset or in the past), and for each:

1. Marks it SENDING, increments `attemptCount`, stamps `lastAttemptAt`, logs
   `DELIVERY_ATTEMPTED`.
2. Calls `ChannelProvider.send(task)` (currently `SimulatedChannelProvider` --
   documented assumption, no real email/SMS/push/webhook integration exists).
3. On success: SUCCEEDED, records `providerMessageId`, logs `DELIVERY_SUCCEEDED`.
4. On failure: classifies via `RetryPolicy` --
   - Retryable (`TRANSIENT_PROVIDER_FAILURE`, `RATE_LIMITED`, `TIMEOUT`) and
     attempts remain -> `FAILED_RETRYABLE`, `nextAttemptAt` set via bounded
     exponential backoff (10s base, doubling, capped at 5 min), logs
     `DELIVERY_FAILED` + `RETRY_SCHEDULED`.
   - Otherwise (`PERMANENT_PROVIDER_REJECTION`, `INVALID_RECIPIENT`,
     `AUTH_ERROR`, or attempts exhausted) -> `FAILED_TERMINAL`, logs
     `DELIVERY_FAILED` (terminal=true).
5. Rolls the outcome up into `Notification.status` (`NotificationStatusRollupService`):
   any task still in flight -> `IN_PROGRESS`; all terminal with a mix of
   success/failure -> `PARTIALLY_DELIVERED`; all succeeded -> `COMPLETED`;
   none succeeded -> `FAILED`.

### Testing the retry/failure paths on demand

`SimulatedChannelProvider` recognizes recipient id prefixes so specific
outcomes can be exercised without relying on randomness:

| Recipient id prefix | Outcome                       | Retryable? |
|----------------------|-------------------------------|------------|
| `invalid-...`         | INVALID_RECIPIENT             | No |
| `blocked-...`         | AUTH_ERROR                    | No |
| `ratelimit-...`       | RATE_LIMITED                  | Yes |
| `flaky-...`           | TRANSIENT_PROVIDER_FAILURE    | Yes (every attempt) |
| anything else         | ~85% success / ~15% transient failure | Yes, when it fails |

## API

### POST /notifications
Submits a notification. Requires `idempotencyKey` -- repeating the same key
returns the original notification (`duplicate: true` in the response) instead
of creating a second one.

```bash
curl -s -X POST http://localhost:8081/notifications \
  -H 'Content-Type: application/json' \
  -d '{
        "idempotencyKey": "order-42-shipped",
        "sourceSystem": "order-service",
        "eventId": "evt-123",
        "notificationType": "ORDER_SHIPPED",
        "severity": "MEDIUM",
        "priority": "NORMAL",
        "recipients": ["user-1"],
        "requestedChannels": ["EMAIL"]
      }'
```

Returns `201 Created` with `{ notificationId, status, duplicate, createdAt }`.

### GET /notifications/{id}
Returns the aggregate status plus per-recipient-per-channel delivery status.

```bash
curl -s http://localhost:8081/notifications/<id>
```

## Channel routing policy (current, documented assumption)

No real recipient-preference store exists in this prototype. `DefaultChannelRouter`:
CRITICAL severity always adds EMAIL + SMS regardless of what was requested;
otherwise the requested channels are used as-is (deduplicated); if none were
requested, defaults to EMAIL. Swappable via the `ChannelRouter` interface.

## Known limitations (to carry into the deliverable writeup)

- Single delivery-worker instance assumed; the claim query is a plain SELECT,
  not a locking claim -- would double-process under multiple worker instances.
  This is also the reason 4.4's "reprocessing a queued delivery must not
  create uncontrolled duplicate side effects" isn't separately implemented:
  with one worker instance there is no concurrent reprocessing to guard
  against yet. A real claim (`SELECT ... FOR UPDATE SKIP LOCKED`) would be
  needed before scaling to multiple worker instances.
- H2 TCP sharing is a dev/assignment convenience with a startup-order
  dependency (api before worker); not a production pattern.
- SimulatedChannelProvider is not a real integration with any channel.
- Retry backoff/cap values (10s base, 5 min cap, up to 5 attempts) are
  reasonable defaults for a prototype, not tuned against real provider SLAs.

## Decisions log

See `docs/DECISIONS.md`.
