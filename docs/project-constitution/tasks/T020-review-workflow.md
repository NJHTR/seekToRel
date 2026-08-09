# T020 Review Workflow and Evaluation Packets

## Objective

Implement first-pass main review, first-pass acceptance review, first-pass rejection review, strict second review, recovery review, and first-pass re-entry as explicit state transitions.

## Invariants

Every review produces score, confidence, summary, reasons, evidence, risks, and next action. Recovery never bypasses first-pass acceptance review. Workflow Gate, not an Agent, changes the business state. Cycles have a bounded re-entry count.

## Tests and integration

Test pass, reject, disagreement, recovery, re-entry pass, re-entry reject, stale evidence, duplicate events, and exhausted recovery budget.

## Done when

The complete review graph can be resumed from an event stream and produces deterministic state transitions.
