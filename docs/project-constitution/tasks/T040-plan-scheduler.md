# T040 Plan Graph and Scheduler Contracts

## Objective

Define dependency-aware plans, serial/parallel/conditional edges, task claims, leases, priorities, resource reservations, idempotency, and cancellation.

## Invariants

No cycles, no unbounded fan-out, no task runs without validated inputs, and no duplicate lease owner. Scheduling is fair, bounded, and restart-safe.

## Tests and integration

Test topological ordering, parallel branches, blocked joins, lease expiry, duplicate dispatch, cancellation, backpressure, and restart recovery.

## Done when

The scheduler can execute a persisted DAG without relying on in-memory process state.
