# Nexus execution observability

The Archive (Agent Observatory) is the user-facing recovery surface for a
ReportAI task. It intentionally renders public execution facts only: status,
stage, percentage, attempts, bounded failure summaries and server-assigned
event sequence numbers. Private prompts and model chain-of-thought are never
displayed.

## Refresh and reconnect behavior

Selecting a task loads its durable event history before opening the SSE stream.
The selected task summary is rebuilt from the history page, so a terminal
success or failure remains visible after a page refresh. New SSE events are
merged by event ID and sequence, then the stream resumes with `Last-Event-ID`.
When the connection drops, the Archive shows the disconnected state and uses
bounded exponential backoff; the explicit Reconnect action is always available
for a user-initiated recovery.

## Action gates

- Retry is rendered only when the server marks the task retryable.
- Export is rendered only when the server marks the task export-ready.
- Failure summaries are redacted and bounded before they reach the UI.

The API remains the source of truth for these gates. The browser must not infer
that a DOCX is releasable solely from a percentage value.

## Responsive behavior

The archive uses a single-column layout below the `lg` breakpoint. Task cards
remain keyboard-accessible buttons, event controls wrap naturally, and the
current activity callout is announced through `aria-live` so a user can follow
the run without opening developer tools.
