# T000 Foundation Contracts

## Objective

Define shared immutable IDs, snapshots, evaluation packets, commands, events, errors, clocks, trace context, and pagination contracts without coupling them to a database or provider.

## Implementation guidance

Use value objects and sealed/result-style contracts where the existing Java style permits. Keep serialization stable and versioned. Add idempotency and correlation identifiers to every command/event.

## Tests and integration

Test equality, serialization compatibility, redaction, validation, error taxonomy, and duplicate command behavior. Verify dependent modules can consume the contracts without importing infrastructure.

## Done when

Contracts are documented, tested, dependency-free, and referenced by at least one real application boundary.
