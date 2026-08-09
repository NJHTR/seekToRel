# Architecture Decisions

## ADR-001 PostgreSQL as primary database

PostgreSQL is selected over MySQL because workflow snapshots, canonical document metadata and agent state benefit from JSONB, strong transactional semantics, rich indexing and future pgvector integration. MinIO stores binaries and Redis stores short-lived coordination/cache data.

## ADR-002 Modular monolith before services

The platform has strong domain boundaries but is still early. A modular monolith keeps transactions and local development manageable while ports preserve a later service extraction path.

## ADR-003 Agents use tools for side effects

This prevents model output from gaining direct host/database/file privileges and gives every side effect a policy, audit log, retry and test boundary.

## ADR-004 Explicit idempotency

The idempotency key is scoped by tenant and operation, compared with a request fingerprint, and backed by a PostgreSQL unique constraint. In-memory implementations are development-only.

## ADR-005 Secure parser before POI

DOCX is an OOXML ZIP archive. The parser spools and checks archive size, entries, expansion ratio, paths, XML entities and external relationships before Apache POI opens it.

## ADR-006 OpenAI-compatible provider boundary

The first concrete provider uses an OpenAI-compatible `/chat/completions` contract so OpenAI, DeepSeek, Qwen, GLM and compatible gateways share one hardened adapter. Credentials are held in `SecretValue`, request options cannot override credentials, and response size/token budgets are bounded before data enters the agent state.

## ADR-007 Durable scheduler with leases

Task submission is persisted before bounded worker dispatch. Claims, lease renewal, retry transitions and completion must be conditional updates in the production PostgreSQL adapter. Lease expiry may cause at-least-once execution, so handlers and tools remain idempotent.

## ADR-008 Review-gated release

Word export is a release operation, not a best-effort rendering call. Completeness review runs before writing the output stream; failed review returns findings and produces no downloadable artifact.

## ADR-009 Asset integrity before persistence

DOCX binaries are extracted and validated as bounded `ParsedAsset` values. Source and embedded assets are uploaded
through immutable `StoredObject` references with SHA-256 verification; a failed upload removes every object created
by the request before idempotency is released. PostgreSQL stores canonical metadata, while object storage owns bytes.

## ADR-010 Live progress is an adapter, not the source of truth

Task progress is exposed through process-local SSE with tenant authorization, per-task subscriber limits, bounded
timeouts and cleanup callbacks. Durable task/workflow state and event tables remain authoritative, so reconnects
must re-read state rather than rely on an unbounded in-memory event backlog.

## ADR-011 Transactional scheduler outbox

Report task creation writes an immutable scheduler submission to `scheduler_outbox` in the same database transaction as
the task and idempotency record. A lease-based reconciler publishes rows to `scheduler_task` after commit. This removes
the request-thread crash window while preserving at-least-once delivery and the scheduler's existing idempotency guard.

## ADR-012 Integrity verification versus deletion

Integrity reconciliation records `UNVERIFIED`, `VERIFIED` or `FAILED` state and retries in bounded batches. Missing or
corrupt objects are not silently deleted. Only metadata whose document/task owner is gone is removed automatically; physical
object deletion requires a separately configured retention policy and audit trail.

## ADR-013 Versioned prompt catalog

Prompt templates are global, versioned records rather than hard-coded strings. PostgreSQL stores immutable version identity,
content and active state; a partial unique index permits exactly one active version per prompt. Rendering remains bounded and
fails closed when a prompt or required variable is missing.

## ADR-014 Bounded conversation context

Conversation history is never passed to an LLM without a provider-neutral token budget. The context orchestrator retains the
newest messages, preserves chronological order, truncates oversized content, reserves response capacity and uses an injectable
compactor for omitted history. The default compactor is deterministic and non-semantic, which keeps local development and
failure handling safe; an optional LLM-backed compactor is wired behind the same port and falls back to the deterministic implementation on provider failure.

## ADR-015 Keyset conversation pagination

Conversation history pages use `(created_at, id)` as an exclusive cursor. Timestamp-only pagination is retained as a compatibility
method, while new callers use the keyset port. Tenant/task ownership tightening is delivered in Flyway V12 so the already
published V11 migration checksum remains unchanged.

## ADR-016 Durable memory snapshots

Compaction writes a tenant-scoped summary plus the exclusive message cursor instead of deleting or rewriting append-only
messages. Snapshot writes use optimistic locking, and PostgreSQL enforces both conversation ownership and cursor-message
ownership with composite foreign keys. Context reads reuse the snapshot and remain bounded even when history is large.

## ADR-017 Scheduled memory compaction

Memory snapshots are maintained by a separate bounded lifecycle worker rather than request threads or report-task
workers. A PostgreSQL candidate query selects only conversations with messages newer than the stored cursor, and
the worker applies a configurable attempt limit. Snapshot CAS is the cross-instance correctness boundary; failed
candidates remain visible for a later cycle. Micrometer counters and a cycle timer make backlog, retries and failures
observable without logging user conversation content.

## ADR-018 Physical orphan-object retention

Object-integrity metadata whose document/task owner no longer exists is not deleted immediately. A bounded scheduled scan
selects only rows older than the configured `report-ai.object-storage.orphan-retention` duration, validates the tenant
namespace before deletion, and protects object keys still referenced by a live owner. Provider bytes are deleted first;
metadata is removed only for successful deletions, so provider failures remain retryable and no metadata is lost before the
physical cleanup succeeds.

## ADR-019 Release quality gates

The Maven Wrapper is pinned to Maven 3.9.12 so local and CI builds use the same resolver. The `quality` profile
keeps the Java 21/Maven Enforcer checks, emits PMD/CPD reports for review, and enforces a 20% line-coverage floor
on the executable application module with JaCoCo. The initial floor is intentionally a measured baseline; it must
only move upward as behavior-focused tests are added. Reports are uploaded by CI rather than mixed into runtime
artifacts.

## ADR-020 Provider-side tool-call guard

Tool-call responses are untrusted provider output. `LlmResponseGuard` validates each response against the request's
declared tool names before application code can execute anything. It also enforces the requested call count, unique
call IDs, required/basic JSON-schema properties, finite numeric values, nesting depth and aggregate argument size.
This remains provider-neutral and runs inside `ValidatingLlmProvider`, so adding another LLM adapter cannot bypass the
same safety boundary.

## ADR-021 Opt-in container integration profile

Infrastructure tests must exercise real PostgreSQL, MinIO, Redis and Docker behavior before release, but local application
development should not require Docker or Testcontainers. The `report-ai-integration-tests` module is therefore attached
only through the explicit Maven `integration` profile and is executed by CI. A successful compilation without that profile
does not count as infrastructure acceptance evidence.

## ADR-022 Expiring idempotency records and CI evidence integrity

An expired idempotency record must not permanently reserve its composite key.
PostgreSQL therefore replaces an expired row only through the same atomic
`INSERT ... ON CONFLICT ... DO UPDATE ... WHERE expires_at <= CURRENT_TIMESTAMP`
statement; concurrent callers still receive at most one successful reservation.
The behavior is covered in the container suite rather than a mock-only test.

CI invokes the checksum-pinned Maven Wrapper, writes OWASP reports to a fixed
directory, uploads SARIF for trusted repository events, and preserves Docker
and Surefire evidence for every integration attempt. A green job without these
artifacts is not treated as release evidence.

## ADR-023 Configurable reasoning effort

OpenAI-compatible providers may expose model-specific reasoning controls. The
application keeps this gateway-specific detail in configuration as optional
`report-ai.llm.reasoning-effort` and sends it as `reasoning_effort` only when
configured, while per-request options can override it. API keys remain process
environment secrets and are never stored in repository files.

## ADR-024 Docker Desktop transport and runtime image readiness

The Docker Java runtime uses the docker-java `zerodep` transport rather than
the Apache HttpClient 5 transport so Windows Docker Desktop named pipes are
handled correctly. The client pins a minimum API compatibility level of 1.40,
which is required by current Docker Desktop engines, and verifies an
allow-listed runtime image before container creation, pulling it with a bounded
timeout when it is not present locally. Runtime image names still originate
only from the immutable `RuntimeConfiguration` allow-list.

## ADR-025 First-party local authentication

Local development and the initial pilot use an internal account provider rather
than an authentication bypass. Registration atomically creates a tenant and its
initial user, password hashes use BCrypt with configurable work factor 12, and
access JWTs contain only validated subject, tenant and allow-listed permissions.
Refresh tokens are opaque random values whose SHA-256 hashes are persisted and
rotated on every use. Reuse of a revoked token revokes all sessions for that
user. The local issuer remains configurable and mutually replaceable with the
existing external JWKS verifier; production signing material must come from a
secret manager, not local files.

## ADR-026 Pluggable credential rate limiting

Credential endpoints (register, login, refresh) are guarded by an AuthAttemptGuard port with two backends: an in-memory fixed window for single-instance/local development, and a Redis fixed window executed atomically through one Lua script for distributed deployments. Both share a single bounded key builder (operation|identity|address) so buckets are portable and inputs cannot craft unbounded keys. The guard fails open when the backing store is unavailable: availability is preserved, and server-side password verification remains the authoritative control. Switching to the Redis backend is a single configuration property, no business-code change.

## ADR-027 Explicit object-store bucket provisioning

The application must not silently create infrastructure buckets in production;
those are owned by deployment/IaC and their credentials should normally lack
bucket-creation privileges. A local profile may opt in through
`report-ai.object-storage.create-bucket-if-missing` so a fresh isolated MinIO
bucket does not block developer acceptance. The adapter performs an idempotent
existence check followed by creation and fails startup if the configured
bucket is still unavailable.

## ADR-028 Explicit PostgreSQL timestamp binding

The PostgreSQL JDBC driver does not infer a SQL type for `java.time.Instant` in
all prepared-statement contexts. Every storage adapter binds temporal write
parameters as `java.sql.Timestamp` and converts result-set timestamps back to
`Instant`. This keeps local and container-backed behavior identical and avoids
late failures during real document/task ingestion.

## ADR-029 Permanent versus transient scheduler failures

Retries are useful only for failures that may change between attempts. Task
handlers can throw `NonRetryableTaskException` for invalid input or missing
mandatory configuration; the scheduler persists `FAILED` immediately and
emits one terminal event. All other failures keep the existing bounded retry
policy, preserving recovery from transient database, runtime and provider
outages without hiding permanent defects behind repeated work.

## ADR-030 Window-level responsive frontend

The portfolio desktop uses CSS container queries for application content rather
than relying only on viewport media queries. A Finder window can be narrow
while a Weather window remains wide, so each `.desktop-window-content` is an
inline-size container and app-specific rules adapt grids, columns, controls and
typography to that local width. Store, Reddit and ReportAI stay in iframes so
their existing viewport-responsive layouts remain isolated from the shell.

## ADR-031 Reference shell assets and shared Notes glyph

Window-control affordances are part of the macOS shell contract, so the shell
uses the reference project's close, horizontal minimize and stretch assets
instead of generic icon-library arrows. Notes is a portfolio-added app rather
than an application from the reference project; its glyph is therefore a
small shared React component used consistently in the Dock, menu and Finder
grid. The browser favicon is explicitly linked so the shell never falls back
to a generic browser globe.

## ADR-032 Responsive layout boundary for embedded Reddit

The Reddit clone is rendered in a portfolio iframe whose width changes with the
macOS window, so a viewport-sized fixed left offset is invalid. The home feed
and community sidebar use a centered CSS Grid at wide sizes; the sidebar is
removed before the feed becomes narrower than its readable width, and the post
controls are capped for very narrow windows. This preserves the original wide
Reddit composition while making the default Nexus window a first-class layout
target. The iframe boundary remains the ownership boundary; the shell does not
reach into Reddit's component state.

## ADR-033 Product and shell naming boundary

`Nexus` is the user-facing experiment-report automation product. `Mac for Web`
is the separate web desktop and portfolio shell that hosts Nexus alongside
other applications. Visible titles, Dock labels, Finder entries and workspace
copy use those names consistently. `ReportAI` remains the repository, backend
and API technical identifier for now; backend module names, environment
variables and route contracts do not receive a risky cosmetic rename. The
standalone frontend package is named `mac-for-web` to match the shell it builds.

## ADR-034 Company operating system before the next product wave

The repository now keeps an explicit multi-agent company operating model under
`.ai-company/`. Product, project-management, architecture, engineering, QA,
security and operations roles have bounded authority and mandatory evidence
gates. Parallel work is allowed only after task ownership and shared contracts
are written; independent review remains required before merge.

This decision responds to the portfolio growing beyond one application. It keeps
Mac for Web, Nexus and future apps independently evolvable while preserving the
existing ReportAI modular-monolith boundaries. The organization docs do not
override `.ai/PROJECT_STATUS.md` or the production release checklist, and they
do not turn unverified end-to-end behavior into a completion claim.

## ADR-035 Task output is the delivery artifact

The report worker writes the review-gated DOCX to object storage and records it
under the successful task's tenant-scoped `TASK/OUTPUT` integrity owner. The
Nexus delivery action therefore calls `GET /api/v1/tasks/{taskId}/export`, which
authorizes the task, requires `SUCCEEDED`, resolves the stored reference and
reads bounded bytes through the checksum-verifying resolver. The existing
`GET /api/v1/documents/{documentId}/export` endpoint remains useful for a
canonical-document rebuild, but it cannot represent generated code, charts or
answers produced by a particular task. Keeping these contracts separate avoids
silently returning the source document when users expect the completed report.

## ADR-036 Fail-closed runtime egress and credential boundary

ReportAI does not attach generated-code containers to Docker's default network.
`networkEnabled=true` is rejected until a managed egress proxy can enforce
tenant allowlists, private-address blocking, DNS-rebinding protection,
budgets, approvals and audit events. Runtime image definitions also require a
content digest, and credential-shaped environment names are rejected before
container creation. This follows Codex's separation between a capability
request and the mechanism that grants it: changing a boolean cannot silently
remove the sandbox boundary.

