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
