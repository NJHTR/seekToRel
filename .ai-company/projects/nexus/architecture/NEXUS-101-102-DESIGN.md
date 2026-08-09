# Nexus Observatory architecture boundary

## Ownership

`report-ai-scheduler` owns the provider-neutral `ProgressEvent` value and the
port used by publishers. `report-ai-storage` owns the PostgreSQL adapter and
Flyway schema. `report-ai-application` owns composition, authorization,
validation and the HTTP DTO/cursor translation. `ProgressEventHub` remains the
live SSE adapter. `nexus-intelligence-workspace` owns Archive/Observatory state
and rendering.

## Invariants

- Persist before or atomically with fan-out; a live subscriber must never be the
  only copy of an accepted event.
- History is append-only and tenant-scoped through the task aggregate. The
  database must enforce uniqueness for the event identity used by retries.
- Reads use a bounded keyset cursor with deterministic `(occurred_at, event_id)`
  ordering. Limits have a server-side maximum and an explicit default.
- A history request first proves task ownership through the existing auth/task
  boundary and returns a generic unavailable result for another tenant.
- Only allow-listed event fields cross the HTTP/React boundary. Event attribute
  keys and values are size-limited and filtered; no prompt, token, raw output or
  stack trace is persisted as a user-facing event.
- SSE remains best-effort. On reconnect the client reads durable history, uses
  event IDs for deduplication, then resumes live streaming.

## Failure behavior

If persistence fails, the publisher reports failure to the scheduler path rather
than pretending the event was durable. If SSE fan-out fails, the durable row
remains. Query timeouts and page limits fail closed with the standard API error
envelope. Schema changes are additive and backward-compatible with the current
task/SSE clients.

## Verification gates

Unit tests cover ordering, cursor boundaries, limits, duplicate identity and
attribute filtering. PostgreSQL integration tests cover restart durability,
tenant isolation and equal-timestamp pagination. Browser checks cover refresh,
reconnect, empty/error/loading states and normal/narrow shell windows. Security
review must explicitly approve the payload allow-list before release.
