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

## Repository workflow

This repo is worked on incrementally with small, reviewed commits pushed
regularly (see `docs/DECISIONS.md` for the commit/PR cadence). Each significant
step (greenfield capability, brownfield enhancement, ambiguous-requirement
resolution) is its own set of commits with a clear message describing intent.
