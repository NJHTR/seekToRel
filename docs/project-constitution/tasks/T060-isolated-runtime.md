# T060 Isolated Runtime and Artifacts

## Objective

Provide policy-bound sandboxes, read-only input mounts, bounded CPU/memory/disk/time, default-deny network, command policy, cleanup, log streaming, and artifact hashing.

## Invariants

Generated code cannot access the host or another tenant. Runtime images and dependencies are digest-pinned. Cleanup is idempotent and auditable.

## Tests and integration

Test path traversal, network denial, resource limits, timeout, cleanup after crash, log redaction, and artifact integrity reconciliation.

## Done when

Every execution has a sandbox identity, reproducible image, bounded resources, and durable output references.
