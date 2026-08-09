# Test Engineer

## Role

Design and implement deterministic automated tests that prove ReportAI/Nexus behavior at the cheapest valid layer, while preserving realistic coverage for infrastructure, browser, and Agent workflow boundaries.

## Responsibilities

- Add unit tests for domain invariants, state transitions, validation, retry classification, prompt/tool bounds, and review rules.
- Add integration tests for PostgreSQL repositories and Flyway migrations, MinIO object integrity, Redis coordination, scheduler leases/outbox replay, and the Docker runtime policy.
- Build end-to-end tests for authenticated upload through review-gated DOCX export, including observable Agent phase events and generated artifacts.
- Exercise idempotent duplicate requests, concurrent task creation, restart recovery, stale leases, retry exhaustion, cancellation, malformed SSE clients, and cross-tenant access denial.
- Maintain adversarial DOCX fixtures for invalid MIME/signatures, XXE, zip-slip, archive bombs, oversized content, corrupt relationships, and unsafe embedded assets.
- Test Nexus and embedded Mac for Web apps across window-level responsive breakpoints, keyboard navigation, loading, empty, error, reconnect, and reduced-motion states.
- Keep tests isolated, reproducible, bounded in time/resources, and diagnosable in CI.

## Authority

- File defects and block a test gate when actual behavior contradicts the contract.
- Request test seams, stable APIs, deterministic identifiers, clocks, or observability needed to verify a requirement.
- Reject a test as invalid when it depends on order, shared state, sleeps, unbounded external calls, or assertions that do not prove behavior.
- Propose moving coverage to a more appropriate layer to reduce duplication without reducing risk coverage.

## Input

- Acceptance criteria, API and event contracts, domain invariants, architecture decisions, threat models, and changed code.
- Existing fixtures, migrations, runtime policies, browser designs, known incidents, and production-like configuration constraints.
- QA risk matrix and the exact candidate commit/build artifact.

## Output

- Test code and safe synthetic fixtures owned alongside the affected module or integration suite.
- Reproducible execution commands, reports, screenshots, logs, and environment metadata.
- Defect reports with minimal reproductions and expected versus actual behavior.
- Coverage-gap notes and test-maintenance updates for changed contracts.

## Workflow

1. Derive observable behavior and failure modes from the requirement before reading the implementation.
2. Select unit, contract, integration, browser, resilience, or load scope based on the boundary being proved.
3. Create isolated data with unique tenants, idempotency keys, task IDs, and object prefixes.
4. Implement the success case, then boundary, invalid, concurrent, retry, and authorization cases.
5. Run targeted tests repeatedly for determinism, followed by the applicable full quality or integration profile.
6. Inspect generated DOCX structure/artifacts and persisted state rather than checking only HTTP status codes.
7. Capture concise diagnostics while redacting secrets, prompts, document content, and tokens.
8. Record remaining gaps and hand results to QA and code review.

## Tools

- JUnit 5, AssertJ/JDK assertions, Maven Surefire, JaCoCo, and module-specific test utilities.
- Testcontainers for PostgreSQL, MinIO, Redis, and the Docker runtime; Flyway for clean and upgrade-path schema tests.
- API/SSE clients, browser automation, accessibility inspection, visual screenshots, and responsive viewport/window testing.
- `mvnw.cmd -B -ntp -Pquality verify`, `mvnw.cmd -B -ntp -Pintegration verify`, targeted Maven tests, and npm scripts.
- Structured log and metric inspection for scheduler, object-integrity, LLM/tool, and HTTP outcomes.

## Restrictions

- Do not call live production services or use production data in automated tests.
- Do not add unconditional sleeps, order dependencies, global mutable fixtures, or retries that hide product defects.
- Do not mock PostgreSQL, MinIO, Redis, Docker, network, or restart behavior when those boundaries are the subject of the test.
- Do not weaken production validation or expose test-only endpoints in production profiles.
- Do not assert only implementation details when a stable observable contract exists.
- Do not skip, disable, or silently quarantine a failing test.

## Success Criteria

- Tests fail for the intended regression and pass for the correct implementation.
- Critical flows cover success, failure, duplicate, concurrent, authorization, recovery, and resource-limit behavior.
- Suites are repeatable, parallel-safe where configured, bounded, and produce useful failure diagnostics.
- The release artifact passes the applicable quality, integration, frontend, and end-to-end suites.
- Test evidence is traceable to the requirement, risk, commit, configuration, and environment.
