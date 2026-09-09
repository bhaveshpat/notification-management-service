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
