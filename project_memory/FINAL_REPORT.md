# Final Report Snapshot

Date: 2026-08-06

On 2026-08-07 the LLM execution boundary was made resilient to the upstream
gateway's approximately two-minute Cloudflare origin limit. Coder now uses a
separate bounded attempt timeout and output budget, then automatically retries
once with a lower reasoning effort after `LLM_TIMEOUT`, HTTP 408 or HTTP 524.
Provider logs contain only task correlation id, provider, status, duration and
stable error code. The scheduler lease/checkpoint/SSE design already keeps the
long-running task independent from the browser connection, matching the
durable start/resume and event-stream model observed in Codex app-server.
Agent stages also emit a bounded heartbeat every 15 seconds while a provider
call is in flight, and backend logs record only start, duration, fallback and
stable error code.
The new focused regression test passes; a fresh live DOCX task after an IDEA
restart remains the required external acceptance evidence.

On 2026-08-07 the visible 45% task failure was reproduced and traced to the
coder's provider call reaching the former 60-second HTTP read timeout. Parsing
and planning succeeded and Docker was never entered. The configurable default
is now 180 seconds (with the same per-request budget), HTTP timeouts map to the stable `LLM_TIMEOUT` code, and Agent
diagnostics retain that code without exposing provider details. The scheduler
also renews active leases at one-third of their duration so a high-reasoning
workflow cannot be recovered and duplicated merely for running longer than the
initial lease. Regression, quality and Docker-backed integration verification
all pass; the integration reactor executed 9/9 PostgreSQL, MinIO, Redis and
runtime tests successfully. The IDEA backend must be restarted and a new task
created because the old task has already exhausted its three attempts.
The optional Spring Mail health indicator now follows the email feature flag,
so an intentionally unconfigured SMTP adapter does not mark an otherwise ready
development instance unhealthy.

On 2026-08-07 the account lifecycle gained an environment-backed
`AccountEmailDelivery` port and optional SMTP adapter. This is only the delivery
boundary: PostgreSQL token adapters, verification/reset HTTP flows, password
rotation and public-registration gating are still required. The credentials
present in the supplied Python script were not copied and must be rotated.

The same date also introduced the first Mac for Web app-manifest boundary. App
titles, Dock membership, default window geometry and declared capabilities now
live in `src/appManifest.ts`; signed manifests, health/version handshakes and
enforced permission prompts remain open release work.

The product strategy now proposes Open Agent Desktop as the platform direction:
Nexus remains the first vertical app, while Evidence Lab, MCP/OTel-compatible
provenance, app manifests, local privacy controls and an Automation Center are
future platform capabilities.

ReportAI is a production-oriented modular monolith baseline at approximately
96% implementation completion. The Java quality and Docker-backed integration
builds are green on Java 21:
all 17 modules compile, existing tests pass, static reports are generated, the
application coverage baseline is met, and the executable Spring Boot JAR is
created.

This is not a production approval. Formal blockers remain: a completed OWASP
CVE scan, real PostgreSQL/MinIO DOCX upload-to-export acceptance evidence,
concurrency/recovery/load validation, real backup/restore drills, production
observability/alerts, and a staging canary with tested rollback. The local
container suite is evidence of infrastructure compatibility; CI and staging
evidence are still required for release. Local PostgreSQL/MinIO SSH tunnels
are development infrastructure only.

The backup/restore procedure is documented in
`docs/operations/backup-restore.md`, but no live restore drill is counted until
an operator runs it in an isolated environment and archives the evidence.

This cycle fixed expired PostgreSQL idempotency-key reuse, added task-level
idempotency and rollback regression coverage, expanded the container suite for
PostgreSQL outbox lease recovery, fixed PostgreSQL `Instant` binding against
the real driver, and made the Docker Java adapter work with Windows named
pipes, API 1.40+ and allow-listed image pulls.

The OpenAI-compatible gateway configuration now supports the requested model
and a configurable high reasoning effort. Its HTTPS endpoint is reachable;
credentialed model execution remains intentionally pending until a rotated key
is injected through the IDEA process environment.

The local application now has a first-party registration/login flow backed by
PostgreSQL migration V15. It creates a tenant and account atomically, stores
BCrypt password hashes, issues tenant-bound short-lived JWTs, persists only
hashed rotating refresh tokens, revokes all sessions after refresh-token replay,
and applies local password/attempt controls. Registration, login, protected JWT
access, refresh rotation, replay rejection and logout were exercised against
the local PostgreSQL instance. This is a local/pilot auth adapter; distributed
rate limits, email verification/recovery, secret-manager key rotation and admin
account management remain production requirements.

The real local HTTP path has verified registration/JWT, MinIO source upload,
Apache POI parsing and PostgreSQL canonical-document persistence. The task
stage correctly fails closed when `REPORT_AI_LLM_ENABLED=false`, so the
planner-to-export path still requires a freshly rotated provider secret.
Permanent task failures now bypass retry backoff, while transient failures
retain the bounded retry policy.
A real PostgreSQL task smoke confirmed the terminal failure at attempt 1 when
the local LLM provider is disabled.

The parent Maven build now enforces Java 21 and Maven 3.9+ for every profile;
Java 17 is rejected during `validate` before stale Java 21 bytecode can run.
Runtime and infrastructure images are pinned by content digest, and the
Docker Java pull adapter supports digest references. Both the Java 21 quality
reactor and the Docker-backed eight-test integration reactor passed after the
hardening changes.

The sandbox integration evidence also confirms execution as UID 65532 with
all capabilities dropped, `no-new-privileges`, read-only rootfs and a bounded
`/tmp` tmpfs. CI/staging must still archive the same evidence for release.

The active frontend is now `nexus-intelligence-workspace`, a React 19/Vite
workspace adapted from the Nexus Forge/Flow/Editor/Inspector design. It uses
the real ReportAI authentication, DOCX upload, task creation, authenticated
SSE, retry and DOCX export APIs. Its narrow same-origin server proxy supports
JSON, multipart upload, streaming events and binary download. TypeScript,
production build and high-severity npm audit are green. The former Vue UI is
kept as a fallback implementation, not the primary launch command.

The first visible task smoke reached 45% and failed because the local IDEA
process did not enable an LLM provider. This was diagnosed from the handler
stage boundary and the durable scheduler error: parsing and PostgreSQL/MinIO
storage succeeded; Planner/Coder execution was correctly blocked by the
fail-closed `REPORT_AI_LLM_ENABLED=false` default. Backend stage logging and
frontend failure-detail rendering were added so future runs show the exact
provider, runtime or export cause in both the console and workbench.

The local execution profile must enable both the LLM provider and the Docker
runtime for a complete report task. The safe defaults keep both disabled when
credentials or Docker are not explicitly supplied; startup now emits clear
warnings for those disabled capabilities.

The active Nexus frontend also now responds to each macOS window's content
width rather than only the browser viewport. Container-query rules cover
Finder, Weather, Photos, Notes, Calculator, Calendar and Trash; the embedded
portfolio applications remain responsive through their iframe viewports.
Browser verification at 600px and 360px reported no horizontal overflow, and
the TypeScript lint plus Vite production build are green.

The shell's visual asset alignment was corrected on 2026-08-06. The traffic
lights now use the original horizontal minimize mark, close mark and stretch
mark; a shared macOS Notes glyph is used in every shell surface; and the
reference favicon is linked and served successfully instead of falling back to
the browser globe icon.

The embedded Reddit application was corrected on 2026-08-06. A legacy fixed
`margin-left: 464px` assumed a full-width browser and left too little room for
the feed in the default Nexus window. The home page now centers a bounded
feed/sidebar grid, removes the sidebar before the feed can collide at narrower
widths, and caps post controls for very narrow windows. Both the Reddit build
and complete Nexus build pass. Browser checks show the default Reddit window
and maximized Reddit window with the feed and sidebar separated and no visible
horizontal overflow. The source project still reports its pre-existing ESLint
warnings; this change adds no new build errors.

The frontend branding is now explicit: `Mac for Web` names the macOS-style
portfolio shell and `Nexus` names the experiment-report application hosted in
it. `ReportAI` remains the backend and repository identifier; API paths,
environment variables and Java module names are intentionally unchanged.

## Organization baseline

The company bootstrap phase is complete and independently audited under
`.ai-company/`. The organization has role contracts, department boundaries,
parallel-work ownership rules, product/project templates, review and release
gates, a portfolio task board and a risk register. This improves execution
discipline but does not change the product release assessment above. The
durable task-event history and Agent Observatory implementation wave is now
complete at code level. PostgreSQL owns the append-only tenant-scoped event
history and server sequence; authenticated SSE replays by `Last-Event-ID`, and
Nexus Archive reconciles history with live frames using an idempotent reducer.
Authenticated LLM DOCX end-to-end and operational release evidence remain open.

The latest hosted smoke test separated infrastructure from provider latency:
PostgreSQL/MinIO, upload, parsing and Planner were healthy; Coder reached the
configured provider but exceeded its approximately two-minute gateway window.
Coder defaults were subsequently bounded to low reasoning, 2,048 output tokens
and a 90-second local attempt. The hosted backend is reachable on port 18080
and Mac for Web on port 3000, with Docker runtime enabled. A runtime-only LLM
secret is still required before claiming a complete report export.

The NEXUS-101/102 acceptance gate is intentionally still open: Docker-backed
PostgreSQL/MinIO restart and tenant tests, browser refresh/reconnect/export
evidence, warmed OWASP scanning, backup/restore drills and staging canary/
rollback evidence have not been produced in this environment.

The browser authentication resilience pass is implemented. Because the backend
uses one-time refresh-token rotation, the Nexus client now single-flights
concurrent refresh calls, persists the rotated pair atomically, clears a stale
session on 401, and applies a short cooldown after 429. This removes the common
"请求过于频繁"/401 loop without weakening replay protection. Frontend lint/build
and Java 21 auth/application tests are green; authenticated browser evidence is
still required for the release gate.

The 2026-08-07 live task log also distinguishes a separate failure class. DOCX
upload and Planner succeeded, but Coder returned `LLM_REQUEST_INVALID` in under
2 ms with no provider HTTP log. The local validating provider rejected the
request before network I/O; the scheduler previously retried this deterministic
configuration/input error three times. Bounded validation diagnostics and
non-retryable classification are now implemented. A restarted backend and one
fresh task are required to capture the exact policy violation.

The exact violation is now confirmed and corrected. The validator's broad
substring match treated the legitimate `max_completion_tokens` option as a
credential. Detection now distinguishes provider generation controls from
credential-shaped names, with regression tests for both paths. A backend restart
is required before the next live task.

The default Coder completion budget is now 4,096 tokens. The validating LLM
policy allows up to 16,384 tokens, aligned with startup configuration checks,
while the lower default protects the configured provider gateway from another
long-response timeout. Higher budgets remain an explicit operator choice.

The Mac for Web development proxy now handles upstream stream errors and client
disconnects. Previously an `ECONNRESET` from the ReportAI stream escaped the
`Readable` pipeline and killed the port-3000 Node process; the proxy now returns
a bounded 502 or closes the affected response while keeping the server alive.
TypeScript lint passes and the restarted root page returns HTTP 200.

## 2026-08-07 live execution diagnosis

The latest server log proves that the xfcxb Coder request is reaching the
provider: one attempt returned HTTP 200 after approximately 75 seconds. The
terminal `TASK_NOT_RETRYABLE` was caused by local response validation rejecting
the provider's aggregate completion usage (`completion_tokens` includes hidden
reasoning overhead), not by an invalid request or disconnected provider. A
separate retry reached Coder successfully but exposed a durable pipeline bug:
the handler-created `runtimeRequest` was present in local state but absent from
the next persisted checkpoint, causing Runtime to reject its input type.

Both defects are now fixed. Chat responses enforce a bounded content-size
guard, and AgentPipelineExecutor merges new execution-context values into the
next checkpoint while preserving durable values. Focused Java 21 verification
passes for the LLM and Agent modules (15 and 19 tests respectively). A rebuilt
backend restart and a fresh DOCX task are still required for live confirmation.

The non-streaming provider call remains a known production gap. The current
90-second Coder attempt can be shorter than the provider's real 101-second
generation latency; streaming HTTP/SSE with separate idle and total deadlines
is the planned long-term fix. Until then, operators can configure a bounded
`PT115S` Coder attempt without changing source code.
