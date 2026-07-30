# ReportAI

ReportAI is an agent-oriented document automation platform. The first product slice is automated university experiment reports; the platform boundary is intentionally generic so the same document, workflow, tool, runtime, and review capabilities can later support technical reports, test reports, and weekly reports.

## Design principles

- LLMs plan and reason; tools perform side effects.
- Agents operate on a canonical `Document`, never on a raw `.docx`.
- Workflows are persisted state machines and are resumable.
- Untrusted code runs behind a runtime boundary (Docker in production), never via `Runtime.exec` in business code.
- Every externally visible command has validation, authorization, idempotency, audit logging, and a trace id.
- Provider-specific LLM details stay behind `LlmProvider`.

## Current baseline

The repository is a compilable modular monolith baseline with secure DOCX ingestion and embedded asset integrity checks, PostgreSQL/Flyway adapters, optional MinIO and Docker runtime wiring, a bounded durable scheduler with authenticated SSE progress, provider-neutral and OpenAI-compatible LLM support, concrete Planner/Coder/Runtime/Document/Reviewer agents, review-gated Word release, tenant-authorized export and a Vue3/NaiveUI task console.

## Modules

| Module | Responsibility |
|---|---|
| `report-ai-common` | Cross-cutting result/error contracts and shared value objects |
| `report-ai-api` | HTTP-facing DTOs and API contracts |
| `report-ai-document` | Canonical document model and parser ports |
| `report-ai-parser` | Secure DOCX parsing and parser limits |
| `report-ai-task` | Tenant-scoped report task aggregate, state transitions, idempotent commands |
| `report-ai-scheduler` | Bounded worker pool, durable leases, retries, idempotency, transactional outbox and progress events |
| `report-ai-auth` | Authentication principal, authorization port, and tenant context boundary |
| `report-ai-agent` | Bounded agent orchestration, LLM/tool-backed agents and completeness gate |
| `report-ai-workflow` | Resumable workflow state machine contracts |
| `report-ai-tool` | Tool SPI, hardened registry, Python and chart tools |
| `report-ai-runtime` | Sandboxed execution ports and policy |
| `report-ai-llm` | Provider-neutral contracts, bounded prompt rendering and OpenAI-compatible HTTP provider |
| `report-ai-word` | Apache POI builder and review-gated release service |
| `report-ai-review` | Deterministic completeness review rules and findings |
| `report-ai-storage` | PostgreSQL repositories, scheduler/outbox stores, integrity reconciliation and object-storage adapters |
| `report-ai-application` | Spring Boot composition root |
| `report-ai-ui` | Vue3 + Vite + TypeScript + NaiveUI upload/task/progress/export console |

## Build

The target runtime is Java 21 and Maven. Local verification is available with Microsoft JDK 21.0.11 and Maven 3.9.12. The source intentionally avoids unnecessary Java-version-specific syntax so the domain contracts remain easy to inspect.

Use the pinned wrapper for reproducible builds (PowerShell):

```powershell
$env:JAVA_HOME = 'C:\Users\NJHTR\.jdks\ms-21.0.11'
& .\mvnw.cmd -B -ntp -Pquality verify
```

Container-backed infrastructure tests are intentionally opt-in; ordinary local development does not require Docker.
Run them in CI or a Docker-enabled staging workstation with:

```powershell
$env:JAVA_HOME = 'C:\Users\NJHTR\.jdks\ms-21.0.11'
& .\mvnw.cmd -B -ntp -Pintegration verify
```

The `quality` profile enforces the Java 21/Maven toolchain, generates PMD/CPD reports, and checks a 20% line
coverage floor for the executable application module. CI uploads these reports for review.

Run the dependency security gate separately with `mvnw -Psecurity verify -DskipTests`; see
[`docs/dependency-security.md`](docs/dependency-security.md) for report and exception handling.

Task creation writes a durable scheduler outbox row in the same PostgreSQL transaction as the report task. A lease-based
reconciler publishes committed rows to the bounded scheduler after commit, allowing restart recovery without a request-thread
enqueue crash window. Object integrity metadata is verified in bounded batches and orphan ownership metadata is reconciled;
physical object deletion is intentionally left to an explicit retention policy.

Prompt templates are versioned in PostgreSQL with one active version per prompt and are rendered through a bounded repository-backed renderer.
Tenant-scoped conversations and append-only messages are persisted through a PostgreSQL adapter with CAS conversation updates,
composite tenant ownership constraints and bounded history reads. Agent prompts use a token-budgeted context orchestrator with
deterministic compaction when older history cannot fit; enabling the LLM provider switches to a bounded semantic summarizer
with deterministic fallback. Durable summaries retain an exclusive message cursor and are reused by later context builds
through optimistic-lock protected PostgreSQL snapshots. A separate bounded maintenance worker periodically compacts
conversations with new messages; its cadence, batch size and retry limit are configured under
`report-ai.memory-snapshot.*`, and Micrometer exposes candidate, success, retry, failure and cycle-duration metrics.

## Local PostgreSQL

Copy `.env.example` to `.env`, set a local password, and run `docker compose up -d postgres`. The application reads all connection values from environment variables and Flyway applies migrations from `report-ai-storage/src/main/resources/db/migration`.

## Delivery milestones

1. Baseline contracts and application composition (this commit).
2. Document ingestion: DOCX -> canonical document JSON, object storage, virus/type/size checks.
3. Persisted workflow/task model with pause/resume/retry and WebSocket progress.
4. Docker runtime adapter with CPU/memory/time/network/file policies.
5. LLM providers, prompt repository, planner/coder/reviewer/document agents.
6. Apache POI builder, review rules, export and end-to-end audit tests.

See [`docs/code-review-checklist.md`](docs/code-review-checklist.md) and `.ai/PROJECT_STATUS.md` for the production review baseline and current completion state.
The release evidence and staging/production gates are tracked in [`docs/production-release-checklist.md`](docs/production-release-checklist.md).

## Frontend

```powershell
cd report-ai-ui
npm install
npm run dev
```

The UI accepts a verified JWT, uploads a DOCX with an idempotency key, creates a task using the returned
`documentId`, streams authenticated progress over SSE, and downloads the review-gated DOCX export.
