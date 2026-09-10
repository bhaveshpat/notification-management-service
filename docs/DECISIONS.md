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
