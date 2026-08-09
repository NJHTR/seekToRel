# ReportAI Project Status

- Version: `0.1.0-SNAPSHOT`
- Completion: `96%` (foundation, secure ingestion/assets, local account authentication, sandbox, durable scheduler outbox, integrity reconciliation with age-based physical orphan cleanup, LLM provider, domain agents, bounded conversation memory/context orchestration with durable snapshots and scheduled compaction, structured tool-call validation, durable event history with sequence cursors and tenant-safe SSE replay, review-gated DOCX release, reproducible Maven quality gates, Nexus React frontend integration)
- Current phase: Phase 2/3/4/6/7 integration and production hardening
- Build target: Java 21 / Spring Boot 3 / Maven
- Local verification: Java 21.0.11 and Maven 3.9.12 toolchain verified successfully

## Completed this cycle

- PostgreSQL Flyway baseline and JDBC adapters for document, task, idempotency, workflow and scheduler data
- Secure DOCX parser with archive/resource/XXE/path traversal controls
- Workflow state machine with CAS repository contract and smoke test
- Authentication principal, permission boundary, tenant context, Remote JWKS cache and fail-closed authorization
- Local user registration/login/logout, BCrypt password hashes, tenant bootstrap, HS256 JWT issuer, refresh-token rotation/replay containment, bounded auth rate limiting and Vue login/registration flow
- Bounded scheduler with retry, leases, idempotency and progress events
- Python/Chart tools and optional Docker runtime wiring
- OpenAI-compatible LLM provider with request/response budgets and secret redaction
- Versioned PostgreSQL prompt repository with active-version uniqueness and bounded renderer wiring
- Tenant-scoped conversation/message persistence ports, in-memory adapter and PostgreSQL/Flyway adapter
- Bounded agent orchestrator, tool-backed/LLM-backed agents and completeness gate
- Completeness review engine and review-gated Word release service
- Tenant-authorized bounded DOCX export HTTP endpoint
- Task-bound review-gated DOCX artifact export with tenant authorization, output-integrity lookup and checksum-verified object reads
- DOCX embedded asset extraction with magic-byte validation, SHA-256 metadata and tenant-confined keys
- Transactional scheduler outbox with PostgreSQL uniqueness, leases, retry publication and restart recovery
- Bounded retention cleanup for published scheduler outbox rows
- Object integrity verification status, bounded checksum scans and orphan metadata reconciliation
- Age-based physical orphan-object cleanup with tenant-confined deletion, live-reference protection and retry-safe metadata removal
- Transactional source/asset object upload with checksum verification and cleanup on failure
- Durable workflow event log with PostgreSQL adapter and no-op in-memory fallback
- Concrete Planner/Coder/Runtime/Document/Reviewer domain agents with bounded state/output handling
- Authenticated bounded task progress SSE endpoint with subscriber limits and lifecycle cleanup
- Tenant-bound streaming object resolver with size/checksum verification on read
- Bounded conversation context-window orchestration with deterministic memory compaction fallback and token budget enforcement
- Keyset conversation history cursor with same-timestamp pagination coverage
- Durable conversation memory snapshots with tenant-bound through-cursor and optimistic-lock updates
- Scheduled conversation snapshot compaction with bounded retry metrics and externalized cadence
- Executable Spring Boot application JAR with CI structure smoke check and Java 21/Maven quality profile
- Vue3/Vite/TypeScript/NaiveUI task console with authenticated upload, task creation, SSE progress and export
- Java execution tool plus runtime injection, environment and concurrency security tests
- Reproducible Maven Wrapper 3.9.12 with Java 21 enforcer, PMD/CPD reports and JaCoCo line-coverage gate
- CI artifact publication for Java quality reports and verified Vue dependency audit/build
- PostgreSQL orphan-integrity cutoff binding fixed with explicit `TIMESTAMP` JDBC type for `Instant` values
- Structured LLM tool-call response validation with declared-tool allow-list, call-count/idempotency checks, schema basics, finite-number rejection and bounded argument depth/size
- Opt-in Testcontainers module covers clean PostgreSQL migrations, MinIO integrity round-trip, Redis health, Docker runtime execution and tenant-scoped scheduler access; eight tests pass locally with Docker Desktop 4.84.0/WSL2, while CI evidence remains required
- Document upload acceptance tests cover real Apache POI parsing, replay/conflict semantics, tenant-scoped keys and concurrent same-key convergence
- In-process pipeline acceptance covers upload → parse → task/outbox → review gate → DOCX export; real PostgreSQL/MinIO execution remains a CI release gate
- Added an operator-facing PostgreSQL/MinIO backup and restore runbook; execution evidence and RTO/RPO measurements remain release blockers
- Fixed PostgreSQL idempotency TTL semantics so an expired reservation is atomically replaced on conflict
- Added task creation idempotency, conflict, tenant-isolation and failed-persistence rollback regression tests
- Added PostgreSQL container tests for expired idempotency reuse and outbox deduplication/lease recovery
- Hardened CI to use the checksum-pinned Maven Wrapper and retain OWASP SARIF plus container test evidence
- Implemented NEXUS-101 durable append-only progress history with PostgreSQL sequence ordering, duplicate-safe append, tenant/task foreign-key ownership, bounded cursor APIs and V18 sequence index migration
- Implemented NEXUS-102 Agent Observatory archive/timeline with authenticated task history, server-sequence reducer, Last-Event-ID reconnect, bounded pagination, safe failure codes and explicit unavailable/unauthorized states
- Added durable-before-live fan-out with bounded retry and `durableFailureCount`; persistence failures no longer masquerade as accepted live history and are operator-visible
- Added configurable OpenAI-compatible `reasoning_effort` support and documented secret-only IDEA environment injection for the xfcxb gateway
- Verified the xfcxb HTTPS endpoint is reachable; live authenticated calls are intentionally pending key rotation and process-environment injection
- Verified the first-party auth HTTP lifecycle against PostgreSQL: registration, login, protected JWT request, refresh rotation, replay rejection, all-session revocation on replay and password-policy rejection
- Real PostgreSQL/MinIO upload acceptance reached the parser and persisted the canonical document after fixing JDBC `Instant` bindings across document/task/conversation/workflow/prompt/integrity adapters. Local profiles can explicitly create the isolated `report-ai` bucket when it is missing; production defaults to IaC-provisioned buckets.
- Scheduler tasks now distinguish permanent failures from transient failures; a missing LLM provider marks the report task failed immediately instead of consuming all retry attempts.
- Docker-enabled integration verification is green again: PostgreSQL/MinIO/Redis/Docker runtime, Redis-backed auth rate limiting and tenant-scoped scheduler access pass 8/8 tests under `-Pintegration` on Java 21.
- PostgreSQL, MinIO, Redis and runtime images are pinned by Docker content digest in Compose, Testcontainers and sandbox defaults; Docker Java now preserves digest references when pulling missing images. The runtime now forces UID 65532, drops all Linux capabilities, enables no-new-privileges and mounts a bounded `/tmp` tmpfs; the real Docker integration test verifies UID and workspace writes.
- Maven Java 21 enforcement now runs in the parent build for every profile, preventing Java 17 from executing stale Java 21 test bytecode; Java 17 fails fast during `validate` and Java 21 quality/integration reactors pass.
- A real PostgreSQL task smoke with LLM disabled now reaches terminal `FAILED` at attempt 1 with `LLM provider is not configured for report task`, confirming permanent failures no longer retry.
- `nexus-intelligence-workspace` is now the active React frontend. Its Nexus Forge/Flow/Editor/Inspector shell is connected to ReportAI auth, DOCX upload, task SSE, retry and export APIs through a narrow same-origin proxy.
- The Nexus macOS portfolio shell now uses window-level container queries. Finder, Weather, Photos, Notes, Calculator, Calendar and Trash adapt to their own window width; 600px and 360px browser checks found no horizontal overflow, and lint/Vite production build remain green.
- The shell visual asset pass is complete: reference close/minimize/stretch traffic-light SVGs, a shared Notes glyph and an explicit reference favicon are wired and browser-verified.
- Nexus export now downloads the immutable `TASK/OUTPUT` artifact produced by a successful report task instead of rebuilding the pre-execution canonical document. The backend endpoint and bounded integrity-verified read are covered by focused tests; real authenticated browser/export evidence remains open.

## Known risks

- Java 21 is the verified local and release toolchain; production deployment still requires an explicit JDK 21 runtime check.
- Semantic LLM memory summaries, durable snapshot persistence, and scheduled bounded compaction with retry counters/timers are available behind configuration. Cross-instance races are resolved by snapshot CAS; losing maintenance attempts remain eligible for a later cycle.
- Conversation/task persistence migration V12 enforces tenant-consistent task ownership through a composite foreign key without modifying V11 checksum.
- Physical orphan-object retention cleanup is now bounded, age-gated and tenant-confined; failed provider deletions retain metadata for retry.
- The orphan-integrity scheduled scan previously failed on PostgreSQL `Instant` type inference; the cutoff parameter now uses an explicit `TIMESTAMP` binding and storage tests pass.
- Optional WebSocket adapter and protocol integration tests remain open. Maven wrapper, PMD/CPD reports, a 20% application JaCoCo line gate, PR high-severity dependency review, an OWASP Java dependency-scan profile, container integration profile, CI report artifacts and a production release gate checklist are now present. The first local NVD database initialization timed out after 30 minutes and still needs validation on a warmed CI cache; container profile still needs a successful CI evidence archive.
- UI dependency audit is clean after the non-force `vue-tsc` 3.3.8 upgrade.
- The local auth limiter is intentionally in-process for a single developer instance. Production multi-replica deployments require gateway/Redis enforcement, a secret-manager-backed signing-key rotation plan, email verification and password reset controls.
- Workflow aggregates should carry explicit tenantId rather than relying only on request context.
- V19 account-lifecycle primitives (one-time verification/recovery token contracts, optimistic account versioning, role history and audit schema) are present as a security foundation; HTTP delivery, transactional PostgreSQL adapters and an email provider are still required before public registration is enabled.

## Current workstream

- Restart the local backend and execute a new authenticated LLM-backed DOCX task
  through review and export. Local Docker-backed verification is green at 9/9;
  CI evidence, browser replay/reconnect evidence and Java dependency/CVE scanning
  remain release gates.

## Release estimate (2026-08-02)

- Internal pilot: `1-3` working days after production-like credentials and a staging object store are available.
- Staging candidate: `2-3` weeks with a small team, or `4-6` weeks as a solo implementation, assuming no new product scope.
- Big-factory production standard: `4-6` weeks with 2-3 engineers, or `8-12` weeks solo. This includes integration/load/security
  evidence, backup/restore drills, observability, staged rollout and rollback verification; implementation percentage alone is not
  a release approval.

On 2026-08-03 the distributed credential rate limiter was implemented and verified. Credential endpoints now use the AuthAttemptGuard port; the default in-memory fixed-window backend keeps single-node development infrastructure-free, and a Redis backend enforces the same bounds atomically through one Lua script. Key building is shared and input-bounded. The 16 auth unit tests and a real-Redis integration test pass, and the full 18-module \-Pintegration verify\ reactor passes 8/8 container tests with the Java 21 toolchain and Docker Desktop/WSL2.

- The embedded Reddit app now uses a window-safe centered feed/sidebar grid instead of its old fixed full-screen offset. Reddit and complete Nexus production builds pass; default and maximized browser checks show no feed/sidebar overlap.

## Company operating system bootstrap (2026-08-06)

The AI software company operating system is now present under `.ai-company/`.
It contains executive, product, project-management, architecture, engineering,
quality, security and operations role contracts; mandatory development, review,
security, Git and release gates; portfolio roadmap/status/task/risk records; and
templates for independently owned projects. A structural audit verified 40 role
files, all required sections, ASCII-only content and four project boundaries.

This is organization infrastructure, not product completion. It does not close
the existing Nexus release blockers: authenticated LLM DOCX upload-to-export,
CI CVE evidence, concurrency/recovery/load validation, backup/restore, and
staging observability/canary rollback.

## Runtime sandbox hardening (2026-08-07)

Runtime execution now fails closed for requested network egress, rejects
credential-shaped environment variables before Docker creation, and requires
immutable SHA-256 image references. Focused runtime tests pass. Managed egress
policy/proxy enforcement and read-only-input/read-write-output mount separation
remain release work.

