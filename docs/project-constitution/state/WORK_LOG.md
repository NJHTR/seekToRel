# Work Log

Append entries; do not rewrite history.

## 2026-08-09

- Created the project constitution, reusable development prompt, AgentGit protocol, task protocol, dependency-ordered task index, and continuation state.
- No implementation task has been completed yet.
- Next action: inspect existing contracts and claim `T000-foundation-contracts`.
- Preserved the existing T000 claim and migrated the repository baseline to clean commit `4b32677`; legacy and nested project Git metadata now live outside the workspace.

## 2026-08-09

- Claimed and completed `T000-foundation-contracts` after inspecting the migrated `main` baseline (`4b32677`, `68cc597`).
- Added dependency-free immutable IDs, SHA-256/canonical JSON, input snapshots, evaluation packets, command/event envelopes, failure taxonomy, trace metadata, pagination, and injectable time ports in `report-ai-common`.
- Agent delivery commit `044b79b40e09fe81d3076669d24e3afabbed4fc2`; main-agent boundary fix `7b9a8c68e4446d796d96510cd824253962b4c7bb`; both authored by `yaopenghao`.
- Verification passed with Java 21: `./mvnw.cmd -pl report-ai-common -am clean test`, executable foundation self-check, and `jdeps --multi-release 21 --print-module-deps` (`java.base`).
- Remaining risks: existing modules still use legacy String/Instant contracts; metadata redaction and AgentGit run metadata remain follow-up work. Next unblocked task is `T010-program-precheck`.

## 2026-08-09

- T010 implementation chain delivered by the contract agent: `ee55d88`, `b74cc4e`, `bb80dae`, `a2c8fa5`, `74fb82c`.
- Independent review approved `74fb82c` after hardening policy/format/schema gates, expiry boundaries, duplicate precedence, tenant/snapshot matching, full precheck identity, context redaction, unknown-rule isolation, bounded inputs, and oversized-input audit results.
- Java 21 verification passed: `./mvnw.cmd -pl report-ai-review -am clean test` (17 tests: 3 existing completeness review, 12 precheck, and 2 document tests).
- T010 remains in `verification`: `ProgramPrecheckEngine` is deterministic and replayable, but Agent/review/recovery entry points do not yet enforce a persisted precheck receipt. Resolver/repository ports and custom rule evaluators are also follow-up integration boundaries.

- T010 Agent-entry integration delivered in `bc6a31c` (following `d20c9be` and `a3885f1`). `ReportTaskHandler` now derives a stable tenant/task/document source snapshot and generates an explicit receipt for every Agent type before execution; the Agent guard is fail-closed for identity, snapshot digest, rule pack, freshness, decision, and route.
- Follow-up hardening delivered in `4e0703f`: precheck identity no longer changes with the evaluation window, and application snapshots use the verified stored-object digest/media type/size instead of hashing an object key when payload metadata is absent.
- Pipeline checkpoints now record the active Agent's receipt identity and only compare it when resuming that same Agent, preventing cross-step receipt confusion. Map-only receipt contexts and null identity rejection are covered by tests.
- Independent review findings were addressed: production wiring, per-Agent checkpoint selection, and receipt snapshot tenant binding. Remaining follow-up risk is receipt persistence/source attestation through `PrecheckResultRepository`; current application wiring deterministically recreates and records receipt identity in the durable checkpoint.
- Verification passed with Java 21 after a separate clean phase: `./mvnw.cmd clean`; `./mvnw.cmd -pl report-ai-review -am test`; `./mvnw.cmd -pl report-ai-agent -am test` (39 Agent tests plus reactor dependencies); `./mvnw.cmd -pl report-ai-application -am test` (application suite and reactor dependencies). The precheck suite includes stable-identity coverage across policy expiry windows.
- T010 completed. Next unblocked foundation task is `T020-review-workflow`.

- T020 claimed on `codex/T020-review-workflow/root` after architecture review of the existing `CompletenessReviewEngine`, common `EvaluationPacket`, and generic workflow lifecycle. The implementation deliberately stayed inside `report-ai-review` and did not bypass the T010 precheck boundary.
- T020 implementation delivered in `fa7aec9`, compatibility/API follow-up in `b883d13`, evidence/idempotency/replay hardening in `a517682`, source-state replay validation in `f4b434a`, and budget/branch replay validation in `7951912`.
- The review gate now models first-pass pending, acceptance/rejection, strict second review, recovery pending, bounded re-entry, approval/rejection, and exhausted states. It validates tenant/task/snapshot/precheck/Agent identity, evidence freshness, parent evaluation chains, CAS versions, duplicate idempotency, persisted duplicate events, and replay transition semantics.
- Independent review initially found cross-Agent receipt acceptance, duplicate-event return races, and replay tampering paths. All findings were fixed and re-reviewed; the final reviewer approved `7951912`. Remaining non-blocking risk: durable PostgreSQL/outbox adapter and real scheduler retry E2E belong to later storage/integration work.
- Verification passed with Java 21: `./mvnw.cmd -pl report-ai-review -am clean test`; `./mvnw.cmd -pl report-ai-agent -am test`; `./mvnw.cmd -pl report-ai-application -am test`.
- T020 completed. Next unblocked foundation task is `T030-recovery-orchestration`.

## 2026-08-09

- Claimed `T020-review-workflow` on branch `codex/T020-review-workflow/root` after verifying the clean T010 baseline (`1add561`) and reviewing existing evaluation, review, and workflow contracts.
- Implementation boundary: extend `report-ai-review` with an explicit review workflow aggregate, append-only events, deterministic gate transitions, recovery/re-entry budgets, and contract tests; preserve T010 precheck receipt contracts and existing completeness review APIs.

## 2026-08-10

- T030 recovery orchestration was implemented in `report-ai-recovery` and registered in the Maven reactor. The module owns deterministic failure classification, typed recovery payloads for input/download/code/dependency/OCR/chart/conflict flows, bounded recovery state and UI status, strategy/precheck/review ports, repair checkpoint identity, and append-only CAS/idempotent in-memory events.
- Implementation commits: `50cc0cb`, `45fb9a8`, and `90ee02a` (`yaopenghao <njhtr1018@outlook.com>`). The final hardening binds command payload digests and failure fingerprints, requires fresh precheck-attested branch/checkpoint identities, enforces OCR confidence thresholds, and validates an explicit replay transition table.
- Agents used: `t030_architecture`, `t030_contract`, `t030_recovery_impl`, and independent `t030_recovery_review`. Review findings on digest binding, replay tampering, checkpoint identity, OCR thresholds, and transition targets were addressed before completion.
- Verification passed with Java 21: `./mvnw.cmd -pl report-ai-recovery -am clean test -q` (7 tests) and `./mvnw.cmd -pl report-ai-review -am test -q`. Remaining integration risk is intentional: scheduler, durable PostgreSQL/outbox, runtime/tool adapters, and frontend wiring belong to T040+ integration tasks.
- T030 completed. Next unblocked foundation task is `T040-plan-scheduler-contracts`.

## 2026-08-10

- Claimed `T040-plan-scheduler-contracts` on `main` at `676ae8c` after verifying a clean workspace and the pushed T030 baseline. Initial owned boundaries are the existing workflow, scheduler, and task contract modules; architecture review must confirm whether a narrower new module is preferable.
- Wave A agents `t040_architecture` and `t040_contract` are reviewing existing DAG, scheduler lease, outbox, task, and persistence contracts. No implementation changes are authorized until their reports establish the ownership and compatibility boundary.

## 2026-08-10

- T040 plan/scheduler contracts delivered in `e72a922` (`report-ai-workflow`) and `225c03e` (`report-ai-scheduler`), both authored by `yaopenghao <njhtr1018@outlook.com>`. The workflow side owns immutable tenant/version-scoped plan definitions, serial/parallel/conditional/JOIN edges, explicit condition allow-lists, bounded DAG validation, deterministic topological ordering, plan-run CAS and replay-safe in-memory persistence. The scheduler side owns typed node references, precheck attestations, priority/fairness ordering, bounded resource requests/reservations, lease/version CAS claims, lifecycle transitions, cancellation request/acknowledgement, restart reconciliation, and idempotent submission.
- Independent review by `t040_integration_review` approved the hardening commit `f2272b9`: convenience plan constructors no longer implicitly allow conditional edges; JOIN references must equal their JOIN target; cancellation idempotency is scoped by tenant and node; cancellation request digests omit `commandId` so a retry with a new command ID remains idempotent. Regression tests were added for each boundary. All T040 code commits were authored by `yaopenghao <njhtr1018@outlook.com>`.
- Verification passed with Java 21 (`JAVA_HOME=C:\Users\NJHTR\.jdks\ms-21.0.11`): `./mvnw.cmd -pl report-ai-workflow,report-ai-scheduler -am test -q`, `./mvnw.cmd -pl report-ai-recovery -am test -q`, and `./mvnw.cmd -pl report-ai-review -am test -q`. The first command was independently rerun by the reviewer and exited 0; only the non-blocking SLF4J NOP warning was emitted.
- T040 completed. Remaining integration risks are intentional: PostgreSQL DAG/node persistence, transactional outbox wiring, scheduler application integration, AgentGit control-plane events, runtime adapters, and UI observability belong to later tasks. Next unblocked task is `T050-agentgit-control-plane`.
