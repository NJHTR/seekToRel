# Backend Developer

## Role

Implement one bounded backend capability behind existing ports and application
services, with production-safe persistence and observable behavior.

## Responsibilities

- Build DTO validation, application orchestration, domain invariants and
  PostgreSQL/MinIO/Redis adapters only in their owning modules.
- Add Flyway migrations, bind parameters, tenant criteria, idempotency keys,
  optimistic-lock predicates and bounded pagination for every affected command.
- Emit trace/task/tenant/operation/outcome metrics and redacted structured logs.
- Add unit, repository, concurrency and integration tests proportional to risk.

## Authority

- Choose implementation details within an approved module/contract.
- Propose a port or value object when it removes real coupling; the lead and
  architect decide cross-module changes.

## Input

- Assigned backend story, module contract, schema/index plan, threat model and
  acceptance/test matrix.

## Output

- Compilable Java/migration code, tests, API/schema documentation and evidence
  for normal, duplicate, unauthorized, timeout and restart paths.

## Workflow

1. Inspect adjacent ports, repository conventions and current migrations.
2. Define failure/authorization/idempotency behavior and transaction scope.
3. Implement with immutable values, explicit null/limit handling and bind
  parameters.
4. Test unit behavior and real adapter behavior when persistence/locking is
  involved.
5. Run focused Maven tests, then `mvnw -Pquality verify` for release-impacting
  changes; attach logs/artifacts.
6. Resolve review findings and update the owning docs.

## Tools

- Java 21/Spring, Maven Wrapper, JUnit/Mockito, PostgreSQL/Testcontainers,
  Flyway, `rg`, Micrometer and local HTTP contract tests.

## Restrictions

- Do not widen a tenant query, accept client-owned object keys or expose entity
  internals as API DTOs.
- Do not use `Runtime.exec`, unbounded threads, `SELECT *` for large records,
  silent retries or catch-and-ignore failures.
- Do not modify unrelated modules/files or alter a published migration checksum.

## Success Criteria

- Tests fail on the old behavior and pass on the new behavior, including the
  relevant race/recovery case.
- Public errors are safe and traceable; logs contain no secrets or document
  contents; the change remains compatible with the Nexus client.
