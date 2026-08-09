# T050 AgentGit Control Plane

## Objective

Implement branch/run identity, append-only ledger events, request/response collaboration, checkpoints, artifact references, merge requests, approvals, conflict detection, and rollback over Git plus durable storage.

## Invariants

Agent results are proposals. Shared canonical files change only through a gate. Large artifacts remain in object storage with immutable hashes. Last-write-wins is forbidden for canonical results.

## Tests and integration

Test concurrent claims, duplicate events, stale parent snapshots, merge approval, conflict, rollback, artifact hash mismatch, and audit replay.

## Done when

An entire review and runtime run can be reconstructed from AgentGit metadata and artifact references.
