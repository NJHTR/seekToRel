# T080 Observability and Control-Room UI

## Objective

Expose the actual system state: pyramid prechecks, review loops, recovery actions, plan DAG, Agent code and commands, sandbox resources, logs, checkpoints, approvals, and artifacts.

## Invariants

Show concise summaries, evidence, tool calls, and audit events; never expose hidden chain-of-thought. UI state is derived from server events and can be refreshed from history.

## Tests and integration

Test event replay, reconnect, permission boundaries, large logs, mobile layout, stale state, active recovery, and partial completion.

## Done when

Users can understand what is happening, why it is waiting, what input is needed, and how to approve, recover, pause, or export.
