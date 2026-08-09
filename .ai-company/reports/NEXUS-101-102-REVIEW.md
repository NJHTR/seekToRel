# NEXUS-101 / NEXUS-102 Independent Review

**Review role:** independent QA, security and architecture reviewer  
**Review date:** 2026-08-06  
**Decision:** **CHANGES REQUIRED — do not approve either item yet**  
**Scope:** durable task-event history/query (NEXUS-101) and the Agent
Observatory task timeline/recovery experience (NEXUS-102).

This review is based on the current worktree before the NEXUS-101/102
implementation wave. It is an evidence-based gate, not a claim that future
changes will have the same findings. Re-run the checklist against the final
diff and the migration that is actually shipped.

## Evidence snapshot

The current implementation has a live, process-local SSE adapter:

- `report-ai-application/src/main/java/com/reportai/application/progress/ProgressEventHub.java:18-21`
  explicitly says that its replay tail is not a persistent audit store.
- `report-ai-application/src/main/java/com/reportai/application/progress/ProgressEventHub.java:36-68`
  subscribes by task ID and replays only in-memory events.
- `report-ai-application/src/main/java/com/reportai/application/progress/ProgressEventController.java:23-27`
  authorizes the task through `TaskHttpApplicationService.get` before opening
  the stream.
- `report-ai-scheduler/src/main/java/com/reportai/scheduler/ProgressEvent.java:6-14`
  contains no tenant, durable sequence, source/fencing token or cursor.
- Migrations currently contain `workflow_event` (V5), but no durable task
  progress-event table. The Nexus archive remains a placeholder at
  `nexus-intelligence-workspace/src/components/ReportWorkspace.tsx:304`.
- `nexus-intelligence-workspace/src/components/ReportWorkspace.tsx:202-217`
  keeps task and activity state only in React memory.
- `nexus-intelligence-workspace/src/components/ReportWorkspace.tsx:271-289`
  opens one SSE stream for the current task but has no durable-history fetch,
  cursor resume or automatic reconnect.
- `nexus-intelligence-workspace/src/reportApi.ts:104-141` parses event data but
  discards SSE IDs and has no `Last-Event-ID`/history contract.

The existing task read boundary is a good starting control:
`TaskHttpApplicationService.java:83-86,110-114,161-169` validates the ID,
queries by the authenticated tenant and returns a bounded not-found error for
an absent resource. The new event API must retain that property at the
repository/query layer; controller-only authorization is insufficient.

## Findings

### NEXUS-E101-01 — P1 — no authoritative durable event source

**Evidence:** `ProgressEventHub` is explicitly process-local, while
`ProgressEvent` has no persistence sequence or tenant identity. A restart,
rolling deploy, node switch or process eviction loses the replay tail. There is
also no current task-event migration/repository/API in the worktree.

**Risk:** users can see a task as running or failed while the evidence needed
to explain it is gone. A second application node can show a different timeline,
and a refresh can silently omit the first stages. This violates the stated
NEXUS-101 outcome and makes incident reconstruction unreliable.

**Required correction:** PostgreSQL must be authoritative. Add a bounded,
append-only event table with a generated monotonic sequence, tenant/task
composite ownership, an idempotent event key, and an index matching the history
query. Publish to SSE only after the durable write is accepted (or explicitly
surface an auditable persistence-gap state); do not present a process-local
tail as history. Include the table in backup/restore and retention jobs.

### NEXUS-E101-02 — P1 — tenant identity is implicit in the event stream

**Evidence:** `ProgressEvent` (`ProgressEvent.java:6-14`) contains only
`eventId` and `taskId`; `BoundedTaskScheduler.publish`
(`BoundedTaskScheduler.java:127-135`) accepts a task ID and arbitrary attributes.
The hub maps subscriptions by task ID only (`ProgressEventHub.java:25-28`).

**Risk:** a future ID collision, a misrouted worker message, or an internal
publisher bug can put one tenant's event into another tenant's stream. A
database adapter that trusts a caller-supplied tenant field without checking
the task row would make the problem worse. UUID task IDs reduce probability;
they are not an authorization boundary.

**Required correction:** derive ownership from a verified `(tenant_id, task_id)`
foreign key or validate a tenant-bearing event against the task row in the same
transaction. Every read must include tenant and task predicates. Keep the
tenant in the persisted record for auditing, but never accept it from a browser
as authority. Add a negative cross-tenant test that expects the same bounded
not-found response and does not reveal whether the task exists.

### NEXUS-E101-03 — P1 — event payload can leak sensitive provider/document data

**Evidence:** `BoundedTaskScheduler.java:111-123` copies an exception message
into the public `error` attribute. `ReportTaskHandler.java:291-294` also emits a
provider/configuration reason as an event attribute. `ProgressEvent.java:13`
only performs `Map.copyOf`; it does not bound keys/values or allow-list fields.

**Risk:** provider responses and exception messages can contain prompts,
document text, file paths, object keys, generated code, tokens or personal data.
Persisting and replaying these values widens the blast radius and can expose
tenant-confidential content to every user with task-read permission. Unbounded
attributes also permit memory/database/SSE payload amplification.

**Required correction:** define a versioned, allow-listed public event schema.
Expose phase, agent type, tool name, safe error code, retryability, artifact
IDs/counts and a trace ID only. Redact/truncate at the event boundary, reject
unknown keys, cap total serialized bytes and validate UTF-8/control characters.
Keep diagnostic detail in redacted server logs with a separate access policy;
never place prompts, document content, generated source, raw provider output or
credentials in an event. Add a regression test with a synthetic secret and
document marker and assert that neither history nor SSE contains it.

### NEXUS-E101-04 — P1 — duplicate and out-of-order delivery is not specified

**Evidence:** event IDs are random UUIDs (`BoundedTaskScheduler.java:133`), and
the scheduler can emit events around lease retries and redelivery
(`BoundedTaskScheduler.java:91-123`). The event model has an attempt number but
no durable sequence, source version or fencing token.

**Risk:** a lease-expired worker may continue emitting after another worker has
claimed the task. Retries can duplicate stage events; a wall-clock sort can
reorder them. The UI may show a later failure followed by an earlier progress
event, or mark a task complete from a stale event. At-least-once scheduling
must not become at-least-once user-visible side effects.

**Required correction:** assign ordering in PostgreSQL (a generated sequence or
an equivalent collision-free `(sequence,event_id)` key), not from
`occurred_at`. Carry a stable producer/delivery key and make inserts idempotent
(`ON CONFLICT`/unique constraint). Bind event acceptance to a scheduler lease
fence/version, or explicitly classify stale events and prevent them from
changing the current projection. Define whether duplicate logical events are
retained for audit or collapsed in the read model; the answer must be stable
for the frontend.

### NEXUS-E101-05 — P2 — subscriber limit has a time-of-check/time-of-use race

**Evidence:** `ProgressEventHub.java:40-47` checks `size()` and then calls
`add()` on a `CopyOnWriteArrayList` without an atomic per-task reservation.

**Risk:** concurrent connection attempts can exceed
`maxSubscribersPerTask`, defeating the resource bound and making SSE a cheap
availability attack. The limit is also process-local, so multiple nodes can
multiply it.

**Required correction:** reserve slots atomically (per-task lock/semaphore or
an atomic counter with rollback) and add a tenant/global/IP budget. For a
multi-node deployment, enforce the hard limit in a shared coordinator or at the
gateway. Add a concurrency test that starts more than the limit at once and
asserts no excess successful streams.

### NEXUS-E101-06 — P2 — subscription-limit failures map to the wrong API outcome

**Evidence:** `ProgressSubscriptionLimitException` is thrown by the hub
(`ProgressEventHub.java:42-44`), but `GlobalExceptionHandler.java:21-55` has no
specific mapping for it. The catch-all therefore turns resource exhaustion into
an internal-server-error response instead of a bounded rate/availability
response.

**Risk:** clients retry a 500 and amplify load; operators cannot distinguish a
capacity policy from a server defect.

**Required correction:** map the condition to the standard rate-limit or
capacity error (with `Retry-After` where appropriate), emit a metric, and keep
the public message generic. Test the HTTP contract, including malformed task
IDs and unauthenticated requests.

### NEXUS-E101-07 — P1 — event sink failure can affect task state/leases

**Evidence:** `BoundedTaskScheduler.java:98` publishes `STARTED` before the
worker `try`; terminal success is persisted at line 106 and then published at
line 107, while failures are handled at lines 110-123. The `ProgressEventSink`
contract is a `void` callback with no failure policy.

**Risk:** a PostgreSQL outage or serialization failure in a synchronous durable
sink can terminate a worker before its failure handling, leave a lease until
expiry, or turn an already-succeeded task into a confusing failure path. If
the task state commits but the event write fails, the user sees a false gap.

**Required correction:** define an explicit transaction/outbox policy. Event
append and task transition should be atomic where they represent one state
change, or the event must be retried from a durable outbox with a visible
`persistence_pending` metric. A telemetry failure must never undo a successful
business transition or cause a non-idempotent task side effect to run again.
Exercise append failure before start, during progress and after terminal state,
then restart and reconcile.

### NEXUS-E102-01 — P1 — Observatory does not survive refresh or node changes

**Evidence:** task/document/status/activity state is local React state at
`ReportWorkspace.tsx:202-217`; the Archive tab explicitly says history is
waiting for a query endpoint at line 304. The only stream is created when a
current in-memory `taskId` exists (`ReportWorkspace.tsx:271-289`).

**Risk:** refreshing, reopening the Mac window, signing in on another device,
or switching backend nodes loses the task timeline and recovery actions. The
user cannot distinguish “not started” from “history unavailable”.

**Required correction:** add an authenticated task summary/list endpoint and a
bounded history endpoint. Persist a selected task ID in a route/deep link or a
safe server-state cache, load the summary/history on mount, and present
explicit loading, empty, unavailable and partial-history states. Never claim
live progress from a timer or local optimistic state.

### NEXUS-E102-02 — P2 — reconnect is not gap-safe and can duplicate events

**Evidence:** `reportApi.ts:104-141` ignores SSE `id:` fields and has no
`Last-Event-ID`; the stream stops on terminal events and silently ignores
non-JSON frames. `ReportWorkspace.tsx:282-284` sets an error on disconnect but
does not fetch a durable gap or reconnect automatically.

**Risk:** a network flap loses events between the last rendered frame and the
next connection. A naïve reconnect would replay duplicates, while the current
implementation leaves a misleading incomplete timeline.

**Required correction:** use the server sequence as the cursor. On connect,
load history through a known cursor, subscribe with `Last-Event-ID` (or an
equivalent opaque cursor), deduplicate by sequence/event key, and reconcile
history before showing “connected”. Distinguish transient connection failure,
task failure and history-retention gaps. Add reconnect tests with events
inserted between page reads.

### NEXUS-E102-03 — P2 — status can regress because of a stale closure

**Evidence:** `ReportWorkspace.tsx:276-280` updates `status` from a callback
whose effect intentionally omits `status` from its dependency list at lines
287-289. For a `PROGRESS` event without a `phase` attribute (for example the
export event emitted by `ReportTaskHandler.java:356`), it falls back to the
stale status captured when the stream was opened.

**Risk:** the Inspector can show `CREATED`/an old stage after the task has
advanced, and an out-of-order event can overwrite a newer state. This damages
trust in the Observatory even when the backend is correct.

**Required correction:** derive the projection from the ordered event sequence
with a reducer, ignore events older than the last accepted sequence, and make
the phase/status schema non-optional for state-changing events. Add a test for
progress events with missing attributes and for out-of-order delivery.

### NEXUS-E102-04 — P2 — activity feed is bounded locally without a history UX

**Evidence:** `ReportWorkspace.tsx:225-227` keeps only the newest 80 local
items. There is no “load more”, cursor, event-count/retention indicator or
partial-history warning.

**Risk:** long-running or retry-heavy tasks silently lose evidence and the user
cannot tell whether the list is complete. This also makes audit and support
reproduction impossible.

**Required correction:** make the server page size explicit, provide
`hasMore/nextCursor`, show a retention/partial-history badge, and virtualize or
window the rendered list without deleting the authoritative history. Keep event
detail collapsed and bounded in the UI.

## Required API and storage contract

The following is the minimum contract for approval; names may change through an
ADR, but the invariants may not.

### Durable record

- `event_id` (producer idempotency key), `event_sequence` (server-assigned
  monotonic ordering key), `task_id`, `tenant_id`, `attempt`, `type`, `phase`,
  safe message/error code, bounded allow-listed attributes, `occurred_at`, and
  schema version.
- Unique constraints prevent duplicate ingestion. A composite foreign key
  `(tenant_id, task_id)` must reference the tenant-owned task row.
- Index `(tenant_id, task_id, event_sequence)` supports the only history read
  pattern. Add retention/partition strategy before volume grows.
- Event rows are append-only; corrections are new events, never in-place edits.

### History endpoint

`GET /api/v1/tasks/{taskId}/events/history?after=<opaque-cursor>&limit=<n>`

- Requires the verified bearer principal and `TASK_READ`; no tenant query/header
  is accepted from the client.
- Validates task ID and cursor, caps `n` (recommended maximum 100), and uses
  keyset pagination. Do not use offset or `occurred_at` alone.
- Cursor is bound to tenant/task and a schema/version; tampering, wrong-task
  reuse and oversized values fail safely. Response contains `items`,
  `hasMore`, `nextCursor`, and an explicit retention-gap marker if the cursor
  predates the available window.
- Missing and cross-tenant tasks return the same bounded not-found contract.
  Responses are `Cache-Control: no-store`; no raw SQL/provider details appear.

### Live stream

- SSE `id` equals the durable sequence (or a stable opaque equivalent), and the
  server honors `Last-Event-ID`.
- A reconnect is safe when history and live delivery overlap. Duplicate frames
  are harmless because the client reducer is idempotent.
- Terminal state is emitted once in the projection, and a disconnect after a
  terminal event is not shown as task failure.

## Threat model and required controls

| Asset / boundary | Threat | Required control | Evidence required |
| --- | --- | --- | --- |
| Tenant task history | IDOR/cross-tenant read | Composite tenant predicate and task ownership check in service and repository | Two tenants, positive/negative API tests; same 404 contract |
| Event writer | Forged/misrouted worker event | Trusted server-side tenant derivation, lease fence, producer key | Stale-worker and wrong-tenant integration tests |
| Event payload | Prompt/document/code/secret leakage | Allow-list, redaction, byte/key/value limits, safe error codes | Synthetic secret-marker test on DB, SSE and UI |
| Cursor | Tampering, replay, cross-task reuse | Opaque signed/bound cursor, strict size and expiry/version checks | Fuzz and wrong-task/tenant cursor tests |
| Pagination | Offset drift, huge limit, DB load | Keyset query, max page size, matching index, `limit+1` has-more | Concurrent insert/page consistency and load test |
| SSE | Subscriber flood, multi-node fan-out, reconnect gaps | Atomic quotas, gateway/distributed limit, durable backfill, `Last-Event-ID` | Concurrent quota test, restart and two-node test |
| Scheduler | Lease expiry/duplicate delivery | Fencing/version checks and idempotent event ingest | Kill/restart worker during every stage |
| Browser | XSS/unsafe data and misleading state | React text rendering, no HTML injection, reducer by sequence, clear states | Payload fuzz, axe/browser tests, reload/reconnect flow |
| Storage lifecycle | Retention/backup/deletion inconsistency | Documented retention, tenant deletion cascade, backup/restore inclusion | Isolated restore and deletion-after-restore evidence |
| Observability | Sensitive logs/metrics | Redacted structured logs, trace/task IDs, no content | Log scan and metric cardinality review |

## Test matrix required before merge

### Backend and persistence

1. Unit-test event validation, message/attribute limits, schema version,
   redaction and cursor encode/decode.
2. Run a clean PostgreSQL/Flyway integration test proving migration, indexes,
   composite tenant ownership, append ordering and duplicate-key behavior.
3. Verify page boundaries (`0`, `1`, max, max+1), empty history, inserted rows
   between pages, stale/expired/tampered cursors and retention gaps.
4. Run two-tenant authorization tests for task summary, history, SSE, retry,
   artifact and export paths. Assert no timing/error distinction that reveals
   another tenant's task.
5. Run concurrent append and duplicate-delivery tests; assert one logical
   event is projected according to the chosen policy and sequence order is
   monotonic.
6. Kill/restart a worker during queued, running, retry, review and export
   stages. Assert history survives, leases recover, stale events cannot regress
   state, and side effects are not repeated unsafely.
7. Inject database/event-sink failures before start, during progress and after
   terminal state. Verify task/lease semantics follow the documented outbox
   policy and no false terminal status is created.
8. Flood progress attributes and subscriber requests; verify bounded memory,
   HTTP rate/capacity responses and useful metrics.

### Frontend and shell

1. Component tests cover loading, empty, partial, unavailable, queued, running,
   retry-waiting, paused, failed, cancelled and succeeded states.
2. Reducer tests prove ordered projection, stale-event rejection, duplicate
   dedupe, cursor continuation and terminal-state idempotence.
3. Browser tests cover login, upload, task creation, refresh, opening Archive,
   load-more, network flap/reconnect, expired access token, cross-tenant 401/404,
   retry explanation and review-gated export.
4. Exercise normal, narrow and maximized Mac for Web windows, keyboard focus,
   screen-reader live announcements, reduced motion and no horizontal
   overflow. Event detail must remain escaped plain text.
5. Verify no access/refresh token, prompt, document content or raw provider
   output is written to the event DOM, URL, local storage or telemetry.

## Explicit acceptance gates

NEXUS-101 is **not accepted** until all are green:

- [ ] PostgreSQL event migration, constraints and indexes are reviewed and
  applied on a clean database and an upgrade path.
- [ ] Events survive application restart and are included in restore evidence.
- [ ] Tenant authorization, generic not-found behavior, cursor binding and
  bounded limits pass API/integration tests.
- [ ] Duplicate, retry, stale-worker and out-of-order semantics are documented
  and tested with real PostgreSQL concurrency.
- [ ] Sensitive attributes are allow-listed/redacted and event-size limits are
  enforced before persistence and SSE.
- [ ] SSE reconnect uses durable IDs/history, has atomic quotas and does not
  couple telemetry failure to business-task failure.
- [ ] Metrics, alerts, retention/deletion and runbook entries are present.

NEXUS-102 is **not accepted** until all are green:

- [ ] Archive/task list loads authenticated server state after refresh and on a
  second device/node.
- [ ] Timeline uses the durable history contract, supports bounded pagination,
  reconnect and gap/partial-history disclosure.
- [ ] Reducer ignores duplicate/stale events and displays the authoritative
  phase/status without stale-closure regressions.
- [ ] Failure, retry, paused, cancellation, unauthorized, offline and export
  states are explicit and actionable; retry is idempotent.
- [ ] Browser/accessibility/responsive tests pass for Mac for Web window sizes,
  and no sensitive event content reaches the DOM or client storage.
- [ ] Frontend type/build/lint evidence and backend API contract evidence are
  archived together with the same commit/artifact.

## Review conclusion

The existing SSE/task authorization is a useful baseline, but it proves only a
live process-local view. It does not prove durable observability, replay
correctness, event confidentiality or recovery. Treat both work items as
`CHANGES REQUIRED` until the gates above have reproducible PostgreSQL,
multi-worker and browser evidence. After implementation, request a fresh
independent security review rather than carrying this report forward as an
approval.

## Follow-up implementation evidence (2026-08-06)

The implementation wave addressed the principal contract gaps identified above:

- `progress_event` is append-only, tenant-scoped and keyed by a PostgreSQL
  sequence; V18 adds the matching `(tenant_id, task_id, event_sequence)` index.
- Duplicate producer IDs return the stored canonical event, including its
  sequence. The composite task foreign key prevents cross-tenant attachment.
- Public event messages and attributes are bounded/allow-listed. Scheduler
  failures expose stable error codes rather than exception/provider text.
- The durable sink retries three times, counts persistence failures and does
  not broadcast a frame until the canonical row is accepted.
- Tenant authorization is performed before subscription; SSE IDs use the
  durable sequence and numeric `Last-Event-ID` is accepted for replay.
- The Nexus Observatory now reads task/history pages, deduplicates by sequence,
  handles bounded pagination and reconnects with exponential backoff.

Java 21 `-Pquality verify` is green (17 modules; all tests and JaCoCo gate),
focused event/history tests are green, and Nexus TypeScript lint plus high-level
npm audits are green. This is a follow-up implementation record, not an
approval: Docker-backed PostgreSQL/MinIO execution, browser refresh/reconnect
tests, warmed OWASP evidence, backup/restore and staging rollback remain open.
The decision therefore stays **CHANGES REQUIRED** until those gates have
reproducible artifacts.
