# Notification Management Service

A Spring Boot microservices prototype that receives alert requests from upstream
systems and delivers notifications to users through configurable channels
(email, SMS, push, etc.).

## Modules (Gradle multi-project build)

| Module              | Port | Responsibility                                                        |
|---------------------|------|-------------------------------------------------------------------------|
| `notification-api`  | 8081 | Accepts notification submissions, exposes status/query API             |
| `delivery-worker`   | 8082 | Async processing: channel routing, provider calls, retries, dedup      |

More services may be added as modules under `settings.gradle`.

## Status

Early scaffold — build config and module skeletons only. See `docs/ARCHITECTURE.md`
for the design in progress and `docs/DECISIONS.md` for a running log of engineering
decisions (useful for review/audit trail).

## Prerequisites

- Java 17+ (the Gradle wrapper will provision it via toolchains if not on your PATH,
  network permitting)
- Git

Gradle itself does not need to be installed — this repo uses the Gradle Wrapper
(`./gradlew`), which downloads the pinned Gradle version automatically on first run.

## Build & run

```bash
# from the repo root
./gradlew build

# run a single module, e.g.
./gradlew :notification-api:bootRun
./gradlew :delivery-worker:bootRun
```

## Trying it end-to-end

1. Start `notification-api` first (it starts a shared H2 server for the dev profile).
2. Start `delivery-worker` (connects to that shared instance).
3. Submit a notification:

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

4. Check its status a few seconds later (replace `<id>` with the `notificationId` returned above):

```bash
curl -s http://localhost:8081/notifications/<id>
```

To exercise retry/failure paths on demand instead of waiting on randomness,
use a recipient id with one of these prefixes: `invalid-...` (non-retryable,
INVALID_RECIPIENT), `blocked-...` (non-retryable, AUTH_ERROR),
`ratelimit-...` (retryable, RATE_LIMITED), `flaky-...` (retryable,
TRANSIENT_PROVIDER_FAILURE every attempt). See `docs/ARCHITECTURE.md` for
full details on the simulated provider and retry policy.

## Repository workflow

This repo is worked on incrementally with small, reviewed commits pushed
regularly (see `docs/DECISIONS.md` for the commit/PR cadence). Each significant
step (greenfield capability, brownfield enhancement, ambiguous-requirement
resolution) is its own set of commits with a clear message describing intent.
