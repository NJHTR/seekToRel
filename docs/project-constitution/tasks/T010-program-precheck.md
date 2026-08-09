# T010 Program Precheck Engine

## Objective

Build the deterministic pyramid layer used before every Agent: format/security checks, schema validation, deduplication, evidence resolution, risk scoring, context reduction, and token-budget routing.

## Invariants

Hard policy violations stop or isolate. Semantic ambiguity routes to an Agent. A precheck records rule-pack version, input hash, findings, and recommended route.

## Tests and integration

Cover safe, rejected, ambiguous, duplicate, oversized, malformed, and adversarial inputs. Verify every review and recovery entry point requires a precheck result.

## Done when

All Agent ports reject missing or stale precheck evidence and the result is observable and replayable.
