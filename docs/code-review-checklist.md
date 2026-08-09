# Enterprise code-review baseline

The review must be evidence based. For each issue record **problem, risk, cause, fix, and test evidence**, then classify it:

- ★★★★★ release blocker: security, data loss, cross-tenant access, unsafe code execution, or correctness defects.
- ★★★★☆ high priority: reliability, idempotency, transaction, concurrency, or major performance defects.
- ★★★☆☆ recommended: maintainability and operability improvements with bounded impact.
- ★★☆☆☆ / ★☆☆☆☆: optimization and style work that does not block release.

## Required API baseline

- Resource-oriented URLs, correct HTTP methods, explicit request/response DTOs, and a stable envelope (`Result<T>`).
- Bean Validation at the boundary; allow-list pagination/sorting; reject unknown enum/state values.
- Authentication and tenant/ownership checks in the application service, not only in the controller.
- Idempotency key for create/import/upload/execute/submit operations; database unique constraint is the final guard.
- `@RestControllerAdvice` maps domain errors to safe public messages and emits a trace id; never return stack traces or secrets.

## Required security baseline

- DOCX/PDF uploads are size/type/signature checked, stored outside the web root, virus scanned, and assigned a server-side name.
- XML parsing disables DTD/external entities; archive extraction prevents zip-slip and decompression bombs.
- Runtime execution uses a sandbox adapter with no host shell access, bounded CPU/memory/time/output, and disabled network by default.
- SQL uses bind parameters; downloads authorize the object and stream through a controlled endpoint.
- Secrets come from environment/configuration management, never source code or logs.

## Required reliability baseline

- State transitions are explicit and optimistic-lock protected.
- Transactions cover only database work; remote calls and code execution happen outside long DB transactions.
- Retries are bounded and classified (transient vs permanent); every side effect is idempotent.
- Structured logs include traceId, taskId, tenantId, operation, duration, and outcome; sensitive payloads are redacted.

## Required performance baseline

- No N+1 queries or remote calls in loops; use batch APIs and bounded concurrency.
- Indexes match actual predicates and ordering; deep pagination uses a cursor where needed.
- Cache only stable/read-heavy data with explicit TTL and invalidation; protect hot keys and negative-cache misses.
- Large files and execution output are streamed/object-stored rather than held in heap.

