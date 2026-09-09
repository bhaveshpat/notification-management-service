# Architecture

_Status: draft — being filled in as we design the greenfield scenario._

## Components

- **notification-api** — public-facing REST API. Accepts notification submissions
  (with idempotency key), persists the initial record, publishes an event/task for
  async processing, and serves status lookups.
- **delivery-worker** — consumes queued delivery tasks, applies channel routing
  policy, calls channel providers, applies retry/backoff for transient failures,
  updates delivery status and audit history.

## Open design questions (to resolve before/while coding)

- [ ] Notification + delivery data model (entities, state machine)
- [ ] API contract (submit, status)
- [ ] Queue/messaging choice between the two services (embedded queue vs. Kafka/RabbitMQ vs. DB-backed outbox)
- [ ] Deduplication boundary + idempotency key storage/retention
- [ ] Channel routing policy representation
- [ ] Retry/backoff strategy and failure classification
- [ ] Audit history storage and content rules (no sensitive payloads/credentials)
- [ ] Persistence choice (in-memory for prototype vs. embedded DB vs. Postgres)

## Decisions log

See `docs/DECISIONS.md`.
