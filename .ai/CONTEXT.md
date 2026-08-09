# Current Context

The repository is a newly scaffolded ReportAI modular monolith. Java 21 is the target and is now available locally through the configured Microsoft JDK 21.0.11 and Maven 3.9.12 toolchain.

The current code has common contracts, document model, parser, task, workflow, auth, runtime, tool, LLM, review, word and storage modules. PostgreSQL migrations cover tenant, document, task, idempotency, workflow, execution, tool log, artifacts and review findings.

The current merged cycle has a successful 17-module Maven Java21 build plus a successful Vue3 production build. It includes Remote JWKS authentication wiring, bounded scheduler/retry/progress contracts, PostgreSQL scheduler and workflow event stores with Flyway V4/V5 migrations, a transactional scheduler outbox with lease-based publication, secure DOCX parsing with embedded asset extraction, transactional SHA-256-verified source/asset uploads, a tenant-bound streaming object resolver, durable object verification status and orphan metadata reconciliation, age-based physical orphan-object cleanup with tenant-confined deletion and live-reference protection, versioned PostgreSQL prompt templates with bounded rendering, tenant-scoped conversation/message ports with a PostgreSQL adapter, keyset conversation history pagination, bounded conversation context-window orchestration with deterministic fallback and optional LLM-backed compaction, durable CAS-protected memory snapshots, scheduled bounded snapshot compaction with retry counters/timers, Python/Java/chart tools, an OpenAI-compatible HTTP LLM provider, bounded Planner/Coder/Runtime/Document/Reviewer agents, completeness review rules, review-gated Word release, tenant-authorized bounded DOCX export with SHA-256 response header, an authenticated bounded task-progress SSE endpoint, optional Docker runtime/tool wiring, a pinned Maven Wrapper with PMD/CPD and JaCoCo quality gates, and a Vue3/Vite/NaiveUI task console. Remaining work is optional WebSocket, Java dependency/CVE scanning, CI execution evidence for the container profile, full upload-to-export acceptance, and production operations runbooks/drills.
## Latest verification (2026-08-02)

- Maven Wrapper is pinned to Apache Maven 3.9.12 (`.mvn/wrapper/maven-wrapper.properties`).
- `mvnw -Pquality verify` passes on Java 21.0.11.
- The quality profile now emits PMD/CPD reports and enforces a 20% line-coverage minimum for `report-ai-application` via JaCoCo.
- CI uploads JaCoCo, PMD and CPD reports as artifacts.
- Removed an unchecked Jackson `Map.class` conversion in `ReportTaskHandler` by using a typed `TypeReference`.
- Fixed the scheduled orphan-integrity query by binding its `Instant` cutoff as `java.sql.Timestamp` with `Types.TIMESTAMP`.
- Added GitHub dependency-review-action for high-severity dependency changes on pull requests; a full Java CVE database scan remains a release gate.
- LLM tool-call responses now pass through `LlmResponseGuard` before application use; undeclared tools, duplicate IDs, excess calls, schema violations, non-finite numbers and oversized/deep arguments are rejected.
- `mvnw -Pquality verify` passes across all 17 modules after the PostgreSQL idempotency fix and task regression suite; application coverage and PMD/CPD gates remain green.
- `npm ci`, the Vue production build and `npm audit --audit-level=high` pass with zero reported vulnerabilities.
- The integration profile runs six container tests, including expired idempotency reuse and outbox lease recovery; all six pass locally with Docker Desktop 4.84.0/WSL2.
- The integration profile now runs seven container tests, including Redis-backed auth rate limiting and MinIO bucket provisioning; all seven pass locally with Docker Desktop Linux engine on Java 21.
- CI now uses the checksum-pinned Maven Wrapper and publishes deterministic OWASP SARIF and container integration evidence.
- Added optional `REPORT_AI_LLM_REASONING_EFFORT` (`low`/`medium`/`high`) and documented xfcxb `gpt-5.6-terra` environment injection without storing the API key. The endpoint is reachable but no authenticated call has been claimed because the chat-exposed key should be rotated first.
- Added an opt-in `report-ai-integration-tests` Maven profile module. It contains Testcontainers checks for clean PostgreSQL migrations, MinIO tenant-confined object integrity, Redis health and a Docker-backed Python runtime. CI runs it; ordinary local builds do not.
- After adding the integration profile, `mvnw -Pquality verify` and `mvnw -Pintegration verify` pass on Java 21.0.11. The Docker Java adapter uses the Windows named-pipe-capable zerodep transport, API 1.40 compatibility and allow-listed image pulls. CI/staging evidence is still required for release.
- The local Spring Boot `local` profile is currently healthy at `GET /api/v1/system/health` (HTTP 200) using the PostgreSQL and MinIO SSH tunnels.
- A real HTTP upload against PostgreSQL/MinIO now succeeds through registration, JWT authorization, MinIO source upload, Apache POI parsing and PostgreSQL canonical-document persistence. The subsequent task intentionally fails closed when no LLM provider is configured (`LLM provider is not configured for agent: planner`); an authenticated provider smoke requires a rotated secret injected into the process.
- Application upload tests now cover real Apache POI DOCX parsing plus same-key replay, content conflict, tenant scoping and concurrent convergence; the suite also covers task/outbox creation, review and DOCX export (seven tests pass).
- Added `docs/operations/backup-restore.md` with isolated PostgreSQL/MinIO backup, restore and evidence procedures; no live drill has been claimed.
- `mvnw -Pquality verify` passes across all 17 modules after the tool-call guard change; LLM module tests now cover eight cases.
- Added a separate OWASP Dependency-Check `security` profile with CVSS 7.0 failure threshold, generated report cache isolation and CI artifact publication.
- The first local OWASP run timed out during NVD database initialization; no vulnerability result is considered valid until the warmed-cache CI job completes.
- LLM tool-call responses now pass through `LlmResponseGuard` before application use; undeclared tools, duplicate IDs, excess calls, schema violations, non-finite numbers and oversized/deep arguments are rejected.
- PostgreSQL JDBC adapters now bind every `Instant` write parameter as an explicit `java.sql.Timestamp`, avoiding driver-dependent type inference failures on real PostgreSQL.
- MinIO bucket provisioning is explicit and profile-controlled: `create-bucket-if-missing` defaults to false and is enabled only by the local profile for the isolated development bucket.
- `NonRetryableTaskException` is handled by the durable scheduler and report handler, so permanent configuration/input failures are persisted immediately while transient provider/runtime failures retain bounded retries.
- Real task smoke evidence: PostgreSQL `scheduler_task.attempt=1`, `status=FAILED` and bounded error `LLM provider is not configured for report task` when the local provider is disabled.
- Runtime and infrastructure image references are now content-digest pinned (`python`, `eclipse-temurin`, `gcc`, PostgreSQL, MinIO and Redis); the Docker Java pull path supports digest references without converting them into mutable tags.
- Maven enforces Java 21 and Maven 3.9+ at the parent `validate` phase for all profiles. Java 17 fails fast with a clear toolchain message; Java 21 quality and integration reactors both pass after the change.
- Docker runtime integration now verifies the sandbox process runs as UID 65532, writes only through the workspace bind, drops all capabilities, uses `no-new-privileges`, read-only rootfs and a bounded non-executable `/tmp` tmpfs.
- The active frontend is now `nexus-intelligence-workspace`: React 19/Vite Nexus Forge, Flow, Editor and Inspector views are wired to ReportAI auth, DOCX upload, task SSE, retry and export through a narrow same-origin proxy. `report-ai-ui` remains a Vue fallback.
- The macOS portfolio shell inside that workspace now marks each desktop window's content as an inline-size container. Container-query rules cover Finder, Weather, Photos, Notes, Calculator, Calendar and Trash, while Store/Reddit/ReportAI remain responsive iframes. Narrow-window checks at 600px and 360px pass without horizontal overflow.
- The shell now also uses the reference traffic-light SVGs, a shared macOS Notes glyph and an explicit `/favicon.ico`, eliminating the previous generic minimize arrow, placeholder Notes glyph and browser globe favicon.
- Reddit's embedded Home layout now centers the feed/sidebar pair with CSS Grid, hides the sidebar below 1040px and caps narrow post controls below 700px. The old `margin-left: 464px` full-screen assumption is gone; normal and maximized Nexus window screenshots both pass.
- Branding is intentionally split: `Mac for Web` is the portfolio desktop shell, `Nexus` is its experiment-report automation application, and `ReportAI` remains the technical backend/repository identifier. Visible frontend labels and route titles follow that split.

## Company operating system (2026-08-06)

The repository now includes `.ai-company/`, the durable operating model for
parallel product work. Role contracts cover executive, product, project
management, architecture, frontend/backend/AI/desktop/platform engineering,
quality, security and operations. Governance defines ready/done criteria,
independent code/security/QA review, Git branch rules, release evidence and
rollback invariants. Portfolio docs register Mac for Web, Nexus, embedded apps
and the shared ReportAI platform; each has explicit ownership boundaries and a
project template.

The first company task wave is `NEXUS-101` durable task-event history and
`NEXUS-102` Agent Observatory. The implementation now includes PostgreSQL
append-only history, server-assigned sequence cursors, tenant-bound task/event
queries, durable-before-live fan-out, `Last-Event-ID` replay and a React
archive/timeline reducer. Production acceptance remains open because Docker
integration execution, browser reconnect evidence and release operations are
not yet archived in this environment. Product and technical status must
continue to be sourced from code/tests and the release checklist, never from
this bootstrap claim alone.
# 2026-08-07 - Task 45% failure resolved in code

- Task `eda0ab1b-a0e7-4e81-919b-638a37adc19e` failed in the coder provider call
  after the old 60-second read timeout; runtime execution was never reached.
- Default provider read timeout is now 90 seconds and timeout failures use the
  safe `LLM_TIMEOUT` code.
- The durable scheduler renews running-task leases every one-third lease duration
  and synchronizes renewal with success/failure version transitions.
- `mvnw -Pquality verify` passed across the 17 production modules.
- `mvnw -Pintegration verify` passed across all 18 modules with 9/9 real
  PostgreSQL, MinIO, Redis and Docker runtime container tests.
- Operational next action: restart the IDEA backend and create a new report task.
  The previous task is terminal and must not be mutated back to queued state.

# 2026-08-07 - Second coder timeout

- New task `78eb9daf-d4ef-401a-9b7f-1dcacfb05ddb` loaded the 90-second fix but
  still timed out at exactly 90 seconds during the high-reasoning coder call.
- HTTP timeout, request budget and LLM policy defaults are now aligned at 180
  seconds; the maximum permitted timeout remains 10 minutes.
- Quality verification passes. Restart IDEA before creating another task.

# 2026-08-07 - Provider rejected coder request

- Task `ed2d9245-8289-4b01-92b0-9d066b147b49` parsed and planned successfully,
  then received a non-2xx provider response after roughly 125 seconds in coder.
- Provider diagnostics now preserve only the HTTP status code and classify
  non-transient 4xx failures as non-retryable. The supplied DOCX was verified
  present at the requested Desktop path.
- The currently running IDEA process predates this diagnostic patch; restart it
  before the next live task.

# 2026-08-07 - Cloudflare 524 confirmed

- Restarted process `37088` loaded provider diagnostics and recorded
  `LLM_PROVIDER_HTTP_524` during coder. The gateway origin timeout occurs before
  Docker execution and is independent of local PostgreSQL/MinIO health.
- Coder now receives a separate configurable reasoning effort through
  `AgentContext`; the default is medium while the global provider setting can
  remain high. Focused tests pass; restart IDEA after this change.

# 2026-08-07 - Adaptive long-running Agent execution

- Codex app-server was reviewed for durable start/resume state, streamed
  process deltas, explicit exit events and connection-independent execution.
- ReportAI now gives Coder a 105-second local attempt budget, bounded output
  tokens and a configurable low-reasoning fallback after HTTP 408/524 or a
  local LLM timeout. Planner/Reviewer keep the global reasoning setting.
- Provider and Agent logs include only task id, stage, provider, duration,
  fallback state and stable error code. A 15-second Agent-stage heartbeat is
  persisted to the progress stream while a provider call is in flight.
- Java 21 focused tests and `-Pquality verify` pass. A fresh live DOCX task
  after an IDEA restart is still required to verify the external gateway.

# 2026-08-07 - Refresh-token replay convergence

- `nexus-intelligence-workspace/src/reportApi.ts` now exposes a per-token
  single-flight refresh helper for the backend's rotating refresh-token model.
- `ReportWorkspace` reads the latest session through a ref, persists rotated
  credentials, signs out on refresh 401, and suppresses refresh storms for ten
  seconds after 429. This addresses stale-token replay and reconnect loops
  without relaxing server-side revocation semantics.
- Frontend lint/build and Java 21 auth/application tests passed. Browser proof
  of refresh, SSE reconnect and Archive recovery remains a release gate.

# 2026-08-07 - Invalid LLM request diagnosis

- Live task logs show upload and Planner provider HTTP 200, followed by Coder
  `LLM_REQUEST_INVALID` in 0–2 ms and no provider request. The failure is local
  request-policy validation, not Cloudflare, Docker, PostgreSQL or MinIO.
- `LlmAgentSupport` now records only bounded validation violation facts; the
  handler classifies `LLM_REQUEST_INVALID` as non-retryable to prevent three
  identical scheduler attempts. Restart IDEA before the next task to load it.

# 2026-08-07 - LLM validator false positive fixed

- The logged violation was `credentials must not be supplied in request options`
  caused by matching any option containing `token`; Coder's legitimate
  `max_completion_tokens` was rejected before provider I/O.
- Validator matching now uses exact credential names and credential-shaped
  suffixes, allowing generation controls. Regression tests cover both cases.
- Restart the IDEA backend before creating the next live DOCX task.

# 2026-08-07 - Hosted Coder timeout diagnosis and bounded defaults

- A hosted smoke task proved upload, parsing and Planner success, then timed
  out in Coder at the provider gateway on both medium and low fallback attempts.
- Coder defaults are now low reasoning, 2,048 completion tokens and a 90-second
  local attempt; the 16,384 server ceiling remains configurable.
- The hosted process was restarted with Docker runtime enabled. Its LLM secret
  was intentionally not copied from IDEA configuration into source, logs or the
  general shell environment, so an operator must inject it at process start for
  the next authenticated DOCX acceptance.

# 2026-08-07 - Transport option filter correction

- The upstream endpoint responds to an unauthenticated request with HTTP 401 in
  under one second, so DNS/TLS/routing are healthy.
- `OpenAiCompatibleLlmProvider` previously filtered every option containing the
  substring `token`, silently dropping `max_completion_tokens`. It now uses the
  same credential-shaped-name rules as the request validator and forwards the
  generation budget. Provider transport tests pass.
