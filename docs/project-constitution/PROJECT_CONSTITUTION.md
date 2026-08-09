# Project Constitution

## 1. Mission

Nexus turns an uploaded experiment report and its approved data into a reproducible, review-gated execution package. Every conclusion must be traceable to a document version, data version, code version, sandbox run, review result, and exported artifact.

## 2. Architectural laws

1. **Canonical model first.** Agents never reason over an unstructured DOCX directly. Parsing produces an immutable canonical document model; later stages consume versioned projections of that model.
2. **Program pyramid first.** Every Agent invocation, including first review, second review, recovery review, planner review, code review, and export review, is preceded by deterministic precheck, normalization, deduplication, evidence resolution, and risk routing.
3. **Agent decisions are proposals.** Agents may create evaluation packets and merge requests. Only the workflow gate may transition a business state.
4. **Recovery is a first-class path.** Missing data, code faults, OCR gaps, dependency failures, and incomplete results enter a typed recovery workflow. They are not converted into an immediate terminal failure.
5. **Recovery never bypasses review.** A second-pass recovery candidate returns to the first-pass acceptance precheck and acceptance reviewer before entering the mainline.
6. **No hidden mutable truth.** Agent chat is auxiliary. The canonical document, task graph, evaluation packets, events, artifacts, and decisions are versioned and auditable.
7. **Untrusted execution is isolated.** Generated code and downloaded data run in a policy-bound sandbox with bounded resources, read-only inputs, default-deny network, and automatic cleanup.
8. **At-least-once events, idempotent effects.** Commands have idempotency keys; event consumers tolerate duplicates; outbox publication is transactional with state changes.
9. **Low coupling through ports.** Domain modules depend on interfaces and value objects. Provider, model, storage, queue, runtime, and Git implementations stay behind adapters.
10. **Reuse over cleverness.** Prefer small pure policies, typed contracts, composition, strategy, state machine, adapter, command, and specification patterns. Avoid deep inheritance and global mutable singletons.
11. **Quality is part of completion.** A task is incomplete until its tests, failure paths, observability, documentation, and integration boundary are verified.
12. **Security is never bypassed for convenience.** A policy block may be appealed or escalated, but an Agent may not silently weaken a policy.

## 3. Domain boundaries

`document`, `review`, `workflow`, `task`, `scheduler`, `agent`, `runtime`, `storage`, `llm`, `tool`, `word`, `api`, and `ui` are separate ownership boundaries. A module may call another module only through a public contract or application port. No module reaches into another module's persistence tables or internal classes.

## 4. Required state model

Documents, review items, plans, runs, recovery attempts, and artifacts have explicit states and append-only transitions. Terminal failure means recovery policy, user input, and escalation paths are exhausted; it does not mean a single command failed.

## 5. Required evaluation packet

Every review-capable Agent returns: `decision`, `score`, `confidence`, `summary`, `reasons`, `evidence_refs`, `risks`, `next_action`, `parent_evaluation_id`, `agent_version`, and `input_snapshot`.

## 6. Completion standard

Do not mark work complete because code compiles. Completion requires: focused unit tests, contract tests for changed ports, an integration path, structured logs/metrics, secure failure behavior, updated task state, and an AgentGit evidence record.
