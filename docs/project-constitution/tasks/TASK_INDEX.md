# Foundation Task Index

These are dependency-ordered foundation tasks, not product phases. Each task establishes a durable boundary used by the next task. Do not skip ahead because a later screen is more visible.

| ID | Task | Depends on | Primary boundary |
|---|---|---|---|
| T000 | Foundation contracts | — | IDs, snapshots, results, errors, clocks, tracing |
| T010 | Program precheck engine | T000 | deterministic gates, rule packs, routing |
| T020 | Review workflow and evaluation packets | T000, T010 | pass 1, pass 2, recovery, re-entry |
| T030 | Recovery orchestration | T010, T020 | missing data, code repair, OCR, dependency recovery |
| T040 | Plan graph and scheduler contracts | T000, T020 | DAG, leases, idempotency, concurrency |
| T050 | AgentGit control plane | T000, T020, T040 | branches, ledger, checkpoints, merge gates |
| T060 | Isolated runtime and artifacts | T040, T050 | sandbox policy, mounts, logs, artifact hashes |
| T070 | Aggregation, final review, and export | T020, T030, T050, T060 | evidence-complete release |
| T080 | Observability and control-room UI | T010-T070 | live states, logs, plans, audit trail |
| T090 | Full integration and quality gates | T000-T080 | replay, failure recovery, load, security |

The next task is always the first `pending` task whose dependencies are `completed`.
