# T030 Recovery Orchestration

## Objective

Replace generic failure/retry behavior with typed recovery for missing data, download approval, code diagnosis and repair, dependency repair, OCR, chart extraction, result conflicts, and user input.

## Invariants

A failure is terminal only after typed recovery policies, user decisions, and escalation are exhausted. Every repair uses a new branch/checkpoint and passes program validation before execution.

## Tests and integration

Exercise each failure class, automatic repair, user-needed state, safe download, OCR confidence threshold, bounded retries, rollback, and partial completion.

## Done when

The UI can distinguish recovering, awaiting input, degraded, policy blocked, and final failure.
