# Nexus Observatory product brief

## Problem

After a DOCX task starts, users can see the live stream only while the browser
and worker are connected. A refresh, reconnect or failed task leaves them unsure
what happened, which stage failed, whether an artifact was produced and what
safe action is available. The Archive tab currently says history is unavailable.

## Users and outcome

The primary user is a student or operator submitting one or more reports. They
need a trustworthy execution record, not hidden model reasoning. The outcome is
that a user can return after a refresh or process restart, identify a task and
its terminal state, inspect bounded public event facts, and retry/export only
when the server says the action is allowed.

## Scope for NEXUS-101/102

- Persist progress events as tenant-scoped, append-only records.
- Query a bounded, ordered history page for a task with a stable cursor.
- Show recent tasks and an event timeline in the Nexus Archive/Observatory.
- Reconnect live SSE and reconcile it with durable history without duplicates.
- Show stage, percent, attempt, timestamps, safe error summary and artifact
  readiness; provide retry/export actions only for the corresponding state.
- Provide loading, empty, expired-session, forbidden/not-found, rate-limit and
  retryable-network-error states in the UI.

## Explicit non-goals

No chain-of-thought, raw prompts, provider responses, credentials, arbitrary
event attributes, cross-tenant search, bulk deletion, or new workflow semantics
are part of this slice. Task history is not a replacement for the durable task
aggregate or workflow event log.

## Acceptance scenarios

1. Given a completed task, when the user opens Archive after a refresh, then the
   task and ordered events are visible and the export action follows server state.
2. Given a failed task, when the user opens it, then the last safe stage and
   bounded failure reason are visible and retry is offered only when permitted.
3. Given two events with equal timestamps, then both appear exactly once in
   deterministic order across page boundaries.
4. Given a cursor/limit outside the documented bounds, the API rejects it with
   the standard validation envelope and performs no unbounded query.
5. Given a task ID from another tenant, the API does not reveal whether the
   resource exists and the UI shows a generic unavailable state.
6. Given a dropped SSE connection, then the UI reconnects and reconciles from
   history without duplicate timeline rows.

## Success metrics

- 99% of accepted task events remain queryable after an application restart.
- At least 95% of task-detail opens render a truthful status within two seconds
  on the supported staging profile.
- Zero cross-tenant history disclosures in negative integration tests.
- Duplicate timeline rows remain zero after three reconnects.

## Privacy and accessibility

Event messages are user-facing operational summaries and are bounded at write
time. The UI must support keyboard navigation, readable focus states, narrow
window layouts and screen-reader labels. Raw document text and model reasoning
remain outside the history payload.
