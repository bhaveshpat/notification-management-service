# Notification Management Service

A Spring Boot microservices prototype that receives alert requests from upstream
systems and delivers notifications to users through configurable channels
(email, SMS, push, etc.).

## Modules (Maven monorepo)

| Module              | Port | Responsibility                                                        |
|---------------------|------|-------------------------------------------------------------------------|
| `notification-api`  | 8081 | Accepts notification submissions, exposes status/query API             |
| `delivery-worker`   | 8082 | Async processing: channel routing, provider calls, retries, dedup      |

More services may be added as modules under the root `pom.xml`.

## Status

Early scaffold — build config and module skeletons only. See `docs/ARCHITECTURE.md`
for the design in progress and `docs/DECISIONS.md` for a running log of engineering
decisions (useful for review/audit trail).

## Prerequisites

- Java 17+
- Maven 3.9+
- Git

## Build & run

```bash
# from the repo root
mvn clean install

# run a single module, e.g.
cd notification-api
mvn spring-boot:run
```

## Repository workflow

This repo is worked on incrementally with small, reviewed commits pushed
regularly (see `docs/DECISIONS.md` for the commit/PR cadence). Each significant
step (greenfield capability, brownfield enhancement, ambiguous-requirement
resolution) is its own set of commits with a clear message describing intent.
