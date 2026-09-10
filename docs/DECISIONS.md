# Engineering decisions log

A running record of notable decisions, in date order, with brief rationale.
Kept so the commit/design history is easy to review.

## 2026-09-09 — Repo structure

- Monorepo (single GitHub repo, Maven multi-module) rather than one repo per
  service — simpler to manage for a small number of related services and keeps
  cross-service changes in one PR/commit.
- Two initial modules: `notification-api` (synchronous submit/status API) and
  `delivery-worker` (async processing) — reflects the asynchronous
  processing/delivery-attempts requirement directly in the service boundary.
- Repo kept **private** on GitHub.

## 2026-09-10 — Build tool: Gradle instead of Maven

- Switched from Maven to Gradle (multi-project build, Groovy DSL) per preference.
- Gradle Wrapper committed (`gradlew`, `gradlew.bat`, `gradle/wrapper/`) so no local
  Gradle install is required — `./gradlew build` works standalone.
- Old `pom.xml` files moved to `_to_delete_maven/` (gitignored, not tracked) pending
  manual deletion — the sandbox this was generated in isn't permitted to delete files
  in this folder.

## 2026-09-10 — Persistence: H2 (in-memory) over SQLite, dual-service data sharing, DB-backed queue

- Chose H2 in-memory over SQLite for the default profile: idiomatic Spring
  Data JPA/Hibernate support, zero install, web console for debugging.
  Documented as an assumption for this assignment (resets on restart).
- Because notification-api and delivery-worker are separate processes, plain
  embedded H2 in-memory would give each its own private (and for the worker,
  empty) database. Resolved by having notification-api start an H2 TCP server
  (H2TcpServerConfig, dev-profile only) so delivery-worker connects to the
  same in-memory instance over jdbc:h2:tcp. Requires starting notification-api
  first; documented as a dev-only convenience, not a production pattern.
- No message broker for the prototype: delivery_tasks itself is the work
  queue (DB-backed queue / transactional-outbox style) -- delivery-worker
  polls for QUEUED-and-due rows. Avoids pulling in Kafka/RabbitMQ for a
  2-3 day prototype while still genuinely demonstrating async processing.
  Documented limitation: claim query is a plain SELECT (not SELECT ... FOR
  UPDATE SKIP LOCKED), correct for a single worker instance only.
- Added a "postgres" Spring profile + root docker-compose.yml on both
  services to show the production evolution path -- config-only swap since
  all DB access goes through Spring Data JPA repositories, no vendor-specific
  SQL. Not required to run the assignment.
- New shared Gradle module `notification-domain` holds the JPA entities and
  repositories used by both services, avoiding duplicated/drifting models.

## 2026-09-10 — Greenfield submit/status API

- POST /notifications + GET /notifications/{id} added in notification-api.
- Idempotency key is required on submission (client-supplied) -- the sole
  dedup boundary (4.4). A repeat with the same key returns the existing
  notification (duplicate: true) rather than creating a second one, and logs
  a DUPLICATE_SUPPRESSED audit event. Documented assumption: no separate
  dedup TTL/retention window implemented yet -- keys are unique for the life
  of the record.
- Channel routing implemented behind a ChannelRouter interface so the policy
  is swappable without touching the submission flow. Default policy
  (documented assumption, no real recipient-preference store in this
  prototype): CRITICAL severity always adds EMAIL + SMS to whatever was
  requested; otherwise requested channels are used as-is.
- Notification.status only reaches ROUTED in this increment -- the
  IN_PROGRESS / COMPLETED / PARTIALLY_DELIVERED / FAILED rollup from
  DeliveryTask outcomes is not yet wired from delivery-worker (next
  increment, alongside real provider calls and retry/backoff).

## 2026-09-10 — Delivery processing: provider simulation, retry policy, status rollup

- ChannelProvider interface + SimulatedChannelProvider: no real channel
  integration exists in this prototype (documented assumption). Recipient id
  prefixes (invalid-/blocked-/ratelimit-/flaky-) let specific outcomes be
  exercised deterministically for testing/demo; otherwise ~85%/15% success/
  transient-failure split.
- RetryPolicy: TRANSIENT_PROVIDER_FAILURE, RATE_LIMITED, and TIMEOUT are
  retryable; PERMANENT_PROVIDER_REJECTION, INVALID_RECIPIENT, and AUTH_ERROR
  are not, regardless of remaining attempts. Bounded exponential backoff
  (10s base, doubling, capped at 5 minutes), bounded by DeliveryTask.maxAttempts
  (default 5).
- FAILED_RETRYABLE is itself a pollable status (with nextAttemptAt) rather
  than bouncing back through QUEUED -- one fewer state transition, and the
  status is more informative to a client checking GET /notifications/{id}
  mid-retry than seeing QUEUED again.
- NotificationStatusRollupService recomputes Notification.status from all of
  its DeliveryTask rows after every transition (any task in flight ->
  IN_PROGRESS; all terminal, mixed outcome -> PARTIALLY_DELIVERED; all
  succeeded -> COMPLETED; none succeeded -> FAILED). Lives in delivery-worker
  since that's where task transitions happen; reads/writes Notification
  directly via the shared notification-domain repository rather than calling
  back into notification-api over HTTP.

## 2026-09-10 — Test suite added

- notification-domain: @DataJpaTest repository tests (real H2, added as a
  test-only dependency there) via a minimal test-only @SpringBootConfiguration
  (TestJpaConfig), since the module itself isn't a Boot application.
- notification-api: Mockito-based unit tests for NotificationService and
  DefaultChannelRouter; @WebMvcTest for NotificationController (HTTP layer
  only, service mocked).
- delivery-worker: Mockito-based unit tests for DeliveryTaskPoller,
  RetryPolicy, SimulatedChannelProvider, NotificationStatusRollupService.
- Deliberately no test exercises the H2-TCP cross-process sharing itself
  (dev-profile-only wiring) -- that's covered by manual verification per the
  README, not worth the complexity of spinning up two JVMs in a test.
