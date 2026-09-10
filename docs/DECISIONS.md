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
