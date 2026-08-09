# Completed

## 2026-08-07 - Adaptive Coder gateway timeout recovery

- Read Codex app-server execution contracts for durable start/resume lifecycles,
  streamed process deltas, explicit exit events and connection-independent task
  state. ReportAI already has the durable equivalent through scheduler leases,
  Agent checkpoints, PostgreSQL progress history and reconnectable SSE.
- Added independent Coder controls: a 105-second attempt timeout, bounded
  `max_tokens`, fallback reasoning effort (`low`) and an opt-in automatic retry
  after `LLM_TIMEOUT`, HTTP 408 or Cloudflare HTTP 524.
- Added safe provider timing/status logs with bounded task correlation ids; no
  prompt, response body, endpoint or credential is logged.
- Added regression coverage proving a 524 on the primary Coder attempt retries
  with the fallback budget and completes without restarting Planner.
- Added a bounded 15-second Agent-stage heartbeat event and structured
  start/completion/fallback logs, so a long provider wait remains visible after
  a browser reconnect without exposing chain-of-thought or prompts.
- Added QQ SMTP environment placeholders only. No mailbox password or
  authorization code was copied from the supplied script.

## 2026-08-07 - Mac for Web manifest boundary

- Audited the OpenAI Codex repository's protocol, app-server, sandbox, MCP and
  review conventions as a reference for boundary design.
- Added `nexus-intelligence-workspace/src/appManifest.ts` and moved Mac for Web
  app titles, Dock ordering, default window geometry and capability declarations
  out of `DesktopShell.tsx`.
- Added the manifest contract documentation. Capabilities are descriptive until
  a permission prompt, health handshake and iframe policy enforce them.
- `npm run lint` and the production build pass; embedded legacy app warnings are
  pre-existing.

## Verified implementation

- Java 21 / Maven 3.9.12 toolchain and pinned Maven Wrapper
- 17-module modular monolith with Spring Boot 3.5.3
- PostgreSQL/Flyway persistence through migration V15
- Tenant-scoped idempotency, task/workflow state, scheduler leases, retries and transactional outbox
- Secure DOCX parsing, embedded asset validation, MinIO storage and tenant-bound streaming reads
- Review-gated Apache POI DOCX export
- Docker runtime boundary plus Python/Java/chart tools
- Provider-neutral LLM contracts, prompt versioning and bounded tool-call response validation
- Planner/Coder/Runtime/Document/Reviewer agents and durable conversation memory snapshots
- Authenticated SSE progress, request tracing, metrics and Vue3/NaiveUI console
- PMD/CPD reports, JaCoCo application baseline gate and CI quality artifacts

## Evidence

`mvnw -B -ntp -Pquality verify` passed on 2026-07-30 with Java 21.0.11.
The application JAR was repackaged successfully and all existing tests passed.

The opt-in integration module now passes against the local Docker Desktop
4.84.0 / WSL2 engine with
`mvnw -B -ntp -Pintegration verify` on 2026-08-04. The eight tests exercised
real PostgreSQL, MinIO, Redis, Docker-backed Python execution and Redis-backed
authentication rate limiting.

The running local Spring Boot profile returned HTTP 200 from
`/api/v1/system/health` through the PostgreSQL/MinIO SSH-tunnel setup.

Document upload acceptance coverage now includes real Apache POI DOCX parsing,
same-key replay, different-content conflict, cross-tenant key scoping and
concurrent same-key convergence (6 tests passing in the application module).

The same application test suite now exercises the business pipeline through
task creation, transactional-outbox enqueue, review gating and DOCX export
(7 tests passing). It intentionally uses bounded in-memory ports; the real
PostgreSQL/MinIO path remains the CI integration gate.

On 2026-08-02 the Java 21 quality reactor passed again after adding four task
idempotency/rollback tests. PostgreSQL idempotency reservations now replace
expired rows atomically instead of leaving a key permanently unusable.

The container integration suite now also covers expired PostgreSQL
idempotency-key reuse and scheduler-outbox business deduplication, owner
validation and expired-lease recovery. PostgreSQL timestamp binding was fixed
for the real JDBC driver, and the Docker Java adapter now supports Windows
named pipes, API 1.40+ and allow-listed image pulls.

CI now executes the pinned Maven Wrapper with a verified distribution SHA-256,
publishes Dependency-Check output from a deterministic directory, uploads
SARIF, and retains Docker/Testcontainers test evidence on success or failure.

The OpenAI-compatible provider now supports an optional global reasoning
effort (`low`, `medium`, or `high`). The requested `gpt-5.6-terra` gateway
configuration is documented with the API key intentionally left as a process
environment secret.

The gateway endpoint was verified as reachable over HTTPS and returned the
expected unauthorized response without credentials; the provider configuration
and reasoning-effort path passed the Java 21 quality build.

Local first-party authentication was completed and verified against real
PostgreSQL: registration, login, bearer access, refresh-token rotation,
replay detection (reuse revokes every session) and logout. Passwords use
BCrypt; refresh tokens are stored as SHA-256 hashes; V15 adds the
`user_account` / `refresh_token` schema. The Vue UI now exposes login/register
and auto-refreshes the access token before expiry.

On 2026-08-03 credential rate limiting was upgraded from a per-JVM, in-memory
fixed window to a pluggable `AuthAttemptGuard`. A new `memory` default keeps
local development infrastructure-free, and a `redis` backend enforces an
atomic distributed fixed window (`INCR` + `EXPIRE` via a single Lua script)
with documented fail-open behavior. Both share one canonical key builder with
input bounds. Auth unit tests (16) pass and a new real-Redis integration test
verified the budget, independent buckets and window reset. The 18-module
`-Pintegration verify` reactor passes 8/8 tests.

The real local HTTP acceptance path now reaches PostgreSQL/MinIO document
upload, Apache POI parsing and canonical-document persistence. PostgreSQL
timestamp writes are explicitly bound as `TIMESTAMP`, and local MinIO can
idempotently provision the isolated `report-ai` bucket. The task stage remains
blocked until a freshly rotated LLM secret is injected; the smoke correctly
fails closed rather than fabricating an AI result.

The scheduler now distinguishes permanent task failures from transient ones;
missing LLM configuration is persisted as an immediate terminal failure rather
than consuming all retry attempts.

A real PostgreSQL task smoke confirmed `attempt=1`, `status=FAILED` and the
bounded configuration error when the local LLM provider is disabled.

On 2026-08-04 the parent Maven build moved Java 21/Maven enforcement into the
base build, so every profile fails fast on an unsupported JDK instead of
running stale bytecode. Runtime, PostgreSQL, MinIO and Redis images are pinned
by content digest; Docker Java preserves digest references during pull. The
Java 21 quality reactor and the eight-test Docker integration reactor both
passed after these changes.

The Docker sandbox now runs containers as UID/GID `65532:65532`, drops all
Linux capabilities, enables `no-new-privileges`, keeps the root filesystem
read-only and mounts a bounded non-executable `/tmp` tmpfs. The real Docker
integration test writes an artifact and asserts the process UID is 65532.

On 2026-08-04 the Vue frontend was redesigned as a visible Agent workbench.
It now includes workspace navigation, a document dropzone, an explicit
post-upload next action, a ten-stage workflow timeline, live progress state,
an event activity log, document/task details, Agent capability cards, parser
warnings and a final DOCX download action. `npm run build` and
`npm audit --audit-level=high` both pass; the running Vite server returned
HTTP 200.

The task failure observability path was then completed. Report task handlers
now log accepted, stage, Agent pipeline, completed and failed transitions with
task ID, attempt, stage and bounded error details. Scheduler failures retain
the durable error while emitting structured logs. The UI now reads the error
attribute from FAILED and RETRY_SCHEDULED progress events, so a missing LLM,
provider rejection or sandbox error is visible instead of only showing the
generic `task failed` message.

The failed-stage timeline mapping was corrected to preserve the stage active
before a terminal FAILED event; later stages remain pending instead of being
painted as successful. Failed tasks can now be retried from the workbench. The
startup validator also warns when the LLM provider or Docker runtime is
disabled, without logging any secret material.

The progress SSE adapter now retains a bounded per-task replay tail in-process,
so a browser that connects after the worker has started still receives recent
accepted/parsing/planning/agent/failure events. The Vue workbench renders phase
or Agent metadata on each activity row, stops the stream on terminal events,
and keeps failure detail visible without exposing chain-of-thought.

On 2026-08-06 the active Nexus macOS portfolio shell gained window-level
responsive behavior. Desktop window content is now an inline-size container;
Finder and Weather use named container queries, while Photos, Notes,
Calculator, Calendar and Trash adapt their grids, typography, spacing and
controls to the actual window width. Embedded Store, Reddit and ReportAI
iframes continue to receive their own responsive viewport. Lint and Vite
production build pass, and browser checks at 600px and 360px found no
horizontal overflow across all applications.

The macOS shell icon pass is also complete: window traffic lights now use the
reference close/minimize/stretch SVGs, the Notes glyph is shared by the Dock,
menus and Finder application grid, and the reference favicon is served from
the active frontend. TypeScript lint and Vite build pass; browser verification
confirmed the three SVG resources, Notes glyph and `/favicon.ico` (HTTP 200).

On 2026-08-06 the embedded Reddit portfolio application was made responsive
inside the normal Nexus window. Its former fixed `margin-left: 464px` only fit a
wide browser viewport and caused the feed to overlap the sidebar at the default
window size. The feed/sidebar pair now uses a centered two-column grid, hides
the secondary column below 1040px and caps content widths below 700px. The
Reddit production build and the complete Nexus production build both pass;
browser verification shows the default window and maximized window without
overlap or horizontal clipping. Existing Reddit ESLint warnings remain
unchanged.

On 2026-08-06 the product and shell naming boundary was made explicit. The
macOS-style portfolio shell is now `Mac for Web`; its experiment-report Agent
application is `Nexus`. Browser titles, desktop menus, Dock/Finder labels,
iframe accessibility titles and the direct workspace title all follow that
model. `ReportAI` remains the backend/repository technical identifier, so no
API endpoint, environment variable or Java package changed.

On 2026-08-06 the repository established the AI software company operating
system requested by the Bootstrap Prompt. `.ai-company` now contains 40
repository-specific role contracts across executive, product, project
management, architecture, engineering, quality, security and operations; Git,
development, code-review, security and release governance; portfolio roadmap,
task board, risk register, decision log and project templates. A PowerShell
structural audit confirmed all required role sections, ASCII-only content, no
credential patterns, and product boundaries for Mac for Web, Nexus,
portfolio-apps and reportai-platform. This organization work intentionally did
not change product code or claim unresolved release gates as complete.

On 2026-08-06 NEXUS-101/102 durable observability implementation was completed.
Progress events now use a PostgreSQL append-only table with tenant/task foreign
key ownership, duplicate-safe producer IDs, database-assigned sequence
ordering, bounded keyset history and a V18 sequence index. Durable fan-out
retries failed writes three times, exposes a failure counter and does not send
an event to live observers until persistence is accepted. Public messages and
attributes are allow-listed and failure details use stable error codes.

The authenticated progress stream now binds subscriptions to the verified
tenant, honors numeric `Last-Event-ID`, emits the durable sequence as SSE `id`,
replays PostgreSQL history on reconnect and maps subscriber exhaustion to 429.
Nexus Archive/Observatory consumes the task/history endpoints, deduplicates by
server sequence, paginates safely and reconnects with bounded backoff. Java 21
quality verification and the focused event/task tests pass. Docker-backed
integration execution, browser replay evidence and production operations
drills remain open release gates.
# 2026-08-07 - Account email delivery boundary

- Added the framework-independent `AccountEmailDelivery` port for verification
  and password-recovery messages.
- Added an optional Spring SMTP adapter with environment-backed host, username,
  password and sender settings. It sends text-only one-time links and never
  logs or persists the raw token.
- The original check-in script's mailbox authorization code and student
  credentials were deliberately not copied; those credentials must be rotated.
- Added the Open Agent Desktop product strategy: Evidence Lab, app manifests,
  MCP/OTel interoperability, local vault and automation center are proposed
  platform work, not release-complete features.

# 2026-08-07 - Runtime sandbox hardening

- Network-enabled execution requests now fail before Docker creation until a
  managed egress policy exists.
- Credential-shaped environment names are rejected while ordinary bounded
  runtime inputs remain supported.
- Runtime image definitions now require immutable SHA-256 digest references.
- Focused runtime tests and the security contract document were added.

# 2026-08-07 - Progress hub startup regression

- Fixed Spring startup failure after `ProgressEventHub` gained a second
  constructor by explicitly marking the production constructor for injection.
- Added an actual Spring application-context regression test so constructor
  selection is verified rather than covered only by direct unit construction.
- The 17-module application test reactor passes; the application module runs
  26 tests with zero failures.

# 2026-08-07 - Coder timeout and scheduler lease recovery

- Diagnosed task `eda0ab1b-a0e7-4e81-919b-638a37adc19e`: parsing and planning
  completed, but all three coder attempts reached the former 60-second HTTP
  read timeout. Docker execution was never entered.
- Raised the configurable LLM read-timeout and default request budget to 180 seconds and map Java
  HTTP timeouts to the stable, non-sensitive `LLM_TIMEOUT` provider code. Agent
  failures now preserve that safe code without exposing endpoints or payloads.
- Added a scheduler lease heartbeat at one-third of the configured lease. Lease
  renewal and terminal transitions share the current optimistic-lock version,
  preventing recovery polling from requeueing a legitimate long-running Agent.
- Added regression tests for provider timeout mapping, safe coder diagnostics and
  execution past the original lease duration. The Java quality reactor passes.
- Restored Docker Desktop and ran `mvnw -Pintegration verify`: all 18 reactor
  modules succeeded and all 9 PostgreSQL, MinIO, Redis and runtime container
  tests passed with zero failures.
- Bound the Spring Mail health indicator to the account-email feature flag. An
  intentionally disabled SMTP adapter no longer makes aggregate Actuator health
  report `DOWN`; enabling email also re-enables the provider health check.

# 2026-08-07 - High-reasoning provider budget adjustment

- New task `78eb9daf-d4ef-401a-9b7f-1dcacfb05ddb` confirmed that the prior 90-second
  default was still too short for the configured `gpt-5.6-terra` high-reasoning
  coder call; planner completed and coder timed out at 90 seconds on all three
  attempts, before Docker execution.
- Unified the provider HTTP read timeout, `ChatRequest` default budget and server
  LLM request policy at 180 seconds, while retaining the existing 10-minute hard
  maximum and safe `LLM_TIMEOUT` diagnostics.
- The quality reactor passes after this change. A restarted IDEA process and a
  newly-created task are required to validate the longer live call.

# 2026-08-07 - Provider HTTP diagnostics

- The next live task reached the provider after parsing and planning, waited
  about 125 seconds, and received a non-2xx response during coder execution;
  Docker was not entered. The old process reduced this to the generic
  `LLM_PROVIDER_ERROR` code, which was insufficient for diagnosis.
- Provider failures now expose only a safe status code such as
  `LLM_PROVIDER_HTTP_400` or `LLM_PROVIDER_HTTP_500`, never the response body,
  prompt, endpoint or credential. Non-transient 4xx failures stop immediately;
  408/429 and 5xx remain retryable.
- The requested DOCX exists at the supplied Desktop path (21,093 bytes). Live
  replay with the new diagnostics is pending an IDEA backend restart.

# 2026-08-07 - Cloudflare 524 mitigation

- The restarted live task exposed the exact upstream response: `LLM_PROVIDER_HTTP_524`.
  Planner completed, while the high-reasoning coder request exceeded the upstream
  gateway's approximately two-minute origin window; Docker was never reached.
- Added a separate configurable coder reasoning effort. Global reasoning remains
  configurable (and can stay `high`), while the coder defaults to `medium` through
  `REPORT_AI_LLM_CODER_REASONING_EFFORT=medium` to keep code generation under the
  gateway limit. The value can be raised deliberately when using a provider with a
  longer origin timeout.
- Agent context now carries bounded LLM options across durable pipeline resumes;
  focused Agent/LLM tests pass. The backend must be restarted once more to load
  this mitigation before the next live task.

# 2026-08-07 - Refresh-token replay convergence

- Nexus now shares one in-flight refresh request per refresh token. Concurrent
  archive/history/SSE callers therefore consume a rotated token once instead
  of replaying the same token and triggering a 401/revocation cascade.
- A refresh 401 clears the browser session and returns the user to sign-in;
  refresh 429 responses enter a ten-second client cooldown so reconnect loops
  cannot immediately exhaust the authentication rate-limit window.
- Frontend TypeScript lint and production build passed. Java 21 auth/application
  reactor passed with 25 auth tests and 26 application tests, all green.

# 2026-08-07 - Invalid LLM request diagnosis

- The latest live task reached Planner successfully, then Coder failed locally
  with `LLM_REQUEST_INVALID` in 0–2 ms before any provider request was sent.
  This is a request-policy validation failure, not a Cloudflare timeout or a
  PostgreSQL/MinIO/runtime failure.
- Validation violations are now logged as bounded policy facts without prompt,
  response or credential content. These failures are marked non-retryable so a
  malformed request no longer consumes all three scheduler attempts.
- Java 21 agent/application reactor passed after the change.

# 2026-08-07 - LLM option validator false positive

- The violation was confirmed as a validator bug: the generic `token` substring
  check rejected the legitimate `max_completion_tokens` generation option.
- Credential detection now uses exact credential names and credential-shaped
  suffixes, while allowing provider controls such as `max_completion_tokens`.
- Added regression coverage for both allowed generation controls and rejected
  `refresh_token`; the Java 21 reactor passes with 13 LLM tests, 18 Agent tests,
  and 26 application tests.

# 2026-08-07 - Coder output budget

- Raised the default Coder completion budget from 2,048 to 4,096 tokens so
  longer experiment scripts are not routinely truncated.
- The server safety ceiling is now 16,384 tokens, matching startup validation.
  Higher values remain an explicit operator choice because they increase
  provider latency and gateway timeout risk.

# 2026-08-07 - Hosted live diagnosis and bounded Coder defaults

- Backend and Mac for Web were hosted outside IDEA on ports 18080 and 3000.
  PostgreSQL and MinIO connectivity remained healthy through the existing local
  tunnel; the frontend root and backend health endpoint returned HTTP 200.
- A real 21 KB Guangzhou Institute DOCX was uploaded and parsed, and a task was
  accepted. Planner completed with provider HTTP 200 in about 48 seconds.
- Coder reached the provider but timed out at the configured 105-second origin
  window on both medium and low reasoning attempts. The scheduler correctly
  placed the task in retry-waiting; Docker was not entered.
- To reduce gateway exposure, Coder defaults are now low reasoning, 2,048 output
  tokens and a 90-second attempt. Java 21 focused tests and packaging pass.
- The hosted backend was restarted with Docker runtime enabled, but the LLM
  provider remains disabled because the IDEA-only runtime secret is not present
  in the safe host process environment. No credential was copied to source or
  logs.

# 2026-08-07 - OpenAI-compatible request option forwarding

- Direct unauthenticated provider probe reached `/v1/chat/completions` and
  returned HTTP 401 in under one second, confirming the endpoint is reachable.
- Code review found a second filtering bug: the HTTP provider discarded every
  option containing `token`, including the legitimate `max_completion_tokens`
  Coder budget. The request validator had already been fixed, but this transport
  filter meant the budget never reached the upstream API.
- The transport now rejects only credential-shaped option names while forwarding
  generation controls. Regression coverage verifies forwarding of
  `max_completion_tokens` and omission of credential-shaped fields; 13 LLM tests
  pass.

# 2026-08-07 - Frontend proxy stream resilience

- Mac for Web's API proxy now handles upstream response-stream errors and
  downstream client disconnects instead of leaving an uncaught `ECONNRESET`
  that terminates the Node process.
- TypeScript lint passes and the frontend was restarted on port 3000; the root
  page returned HTTP 200 after the fix.

# 2026-08-07 - Agent pipeline recovery and provider usage compatibility

- Live logs confirmed three separate failure classes: Coder requests can exceed
  the 90-second local attempt, the xfcxb gateway can return a successful
  response whose aggregate completion usage includes reasoning overhead beyond
  `max_completion_tokens`, and the Runtime step could lose a handler-derived
  `runtimeRequest` between durable agent checkpoints.
- Chat response validation now enforces a provider-independent 512 KiB content
  boundary instead of rejecting a successful stopped response solely because a
  reasoning provider reports aggregate completion usage above the visible
  output budget.
- Agent pipeline execution now merges new values from the current execution
  context into the next durable checkpoint without overwriting authoritative
  persisted values. This preserves derived runtime inputs after Coder.
- Added regression coverage for reasoning usage compatibility and derived
  runtime state recovery. LLM/Agent reactor tests pass: 15 LLM tests and 19
  Agent tests, with no failures.

# 2026-08-07 - Runtime context refresh and SSE disconnect hardening

- A live retry exposed that the report handler created `runtimeRequest` after
  Coder, but the `AgentContext` used by the next loop iteration was an earlier
  immutable snapshot. The handler now rebuilds the context before each agent
  step, while the pipeline executor merges new context values into durable
  state.
- SSE replay and live sends now treat client/proxy disconnects as normal stream
  completion instead of routing an error response through a committed
  `text/event-stream` response.
- Full application reactor verification passes: 17 modules, 27 application
  tests, plus all dependent module tests with Java 21.
