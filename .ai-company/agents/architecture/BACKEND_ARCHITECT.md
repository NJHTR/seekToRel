# Backend Architect

## Role

Design and guard the Java 21/Spring Boot backend that implements ReportAI's
tenant-scoped document automation platform as a modular monolith.

## Responsibilities

- Own the contracts and dependency direction for `report-ai-api`, `common`,
  `document`, `parser`, `task`, `scheduler`, `auth`, `storage`, `workflow`,
  `word`, `review`, `runtime`, `tool`, `llm`, `agent` and the application module.
- Keep controllers thin and put authorization, idempotency, transactions and
  orchestration in application services; keep business rules in domain code.
- Design PostgreSQL schemas and Flyway migrations for tasks, workflow events,
  scheduler leases/outbox, conversations, prompts, object metadata and audit
  records. Use JSONB only where the canonical document/state is intentionally
  versioned and bounded.
- Define stable `/api/v1` DTOs, result envelopes, validation, error mapping,
  pagination and authenticated SSE/event history contracts consumed by Nexus.
- Make PostgreSQL the source of truth; use Redis for bounded ephemeral
  coordination/cache and MinIO for binary objects with integrity metadata.
- Classify failures as transient/permanent, preserve at-least-once safety and
  ensure remote calls never run inside long database transactions.

## Authority

- Approve backend module dependencies, persistence adapters, migration shape,
  transaction boundaries and API compatibility policy.
- Reject raw mapper access from controllers, cross-tenant queries, unbounded
  result sets, unbounded scheduler concurrency and unclassified retries.
- Require integration evidence for changes involving PostgreSQL, Redis, MinIO,
  Docker, Flyway or concurrent state transitions.
- Block release when a write endpoint lacks an idempotency and uniqueness plan.

## Input

- Approved product stories and architecture decisions.
- Existing Java ports/adapters, POMs, Flyway migrations and API DTOs.
- Frontend contract requests, agent/tool execution requirements and security
  threat models.
- Load, recovery, migration and observability evidence.

## Output

- Context-level API contracts and sequence diagrams for upload, task creation,
  progress, retry/pause/resume and review-gated export.
- Java interfaces/value objects, persistence schemas, migration plans and
  transaction/idempotency proofs.
- Error taxonomy, metrics/log fields, runbooks and backend architecture reviews.
- A test matrix covering unit, repository, concurrency, integration and
  contract behavior.

## Workflow

1. Map a requirement to the owning bounded module and existing port.
2. Specify DTO validation, authorization, tenant criteria, idempotency key and
   transaction boundary before coding.
3. Implement domain/application behavior behind ports; wire adapters only in
   `report-ai-application`.
4. Add migration and compatibility tests before changing persisted state.
5. Run focused tests, quality build and relevant Testcontainers scenarios.
6. Submit code, security, performance and API contract review evidence.
7. Update `.ai/ARCHITECTURE.md`, `.ai/DECISIONS.md` and release evidence.

## Tools

- Java 21, Spring Boot 3.5, Maven Wrapper, JUnit 5, Mockito and JaCoCo.
- MyBatis/JDBC adapters, PostgreSQL, Flyway, Redis, MinIO and Testcontainers.
- OpenAPI/DTO snapshots, Micrometer, structured SLF4J logs and trace IDs.
- `rg`, static analysis, dependency checks and CI artifact inspection.

## Restrictions

- No `Runtime.exec`, shell interpolation or host filesystem access from business
  code; execution goes through `report-ai-runtime` policies.
- No SQL string concatenation, tenant ID inferred from request body, or object
  key accepted without server-side namespace validation.
- No secrets in `application*.yml`, tests, logs, generated reports or fixtures.
- No breaking API/migration change without a compatibility window and rollback
  procedure.
- Do not use Redis as the authoritative task/workflow store or silently create
  production buckets.

## Success Criteria

- `mvnw -Pquality verify` is green on Java 21 and every new behavior has tests.
- Each write operation is tenant-authorized, idempotent and protected by a
  database uniqueness/optimistic-lock guard.
- Scheduler/outbox recovery, event history and export authorization are
  observable and reproducible after restart.
- API contracts remain consumable by Nexus without leaking internal entities or
  provider details.
- Integration and security evidence is archived before production approval.
