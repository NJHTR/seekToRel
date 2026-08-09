# ReportAI Production Release Checklist

This checklist defines the evidence required before a production release. A
green Maven build alone is not a production approval: the release must also
pass integration, security, resilience and operational checks.

## P0 - must pass before staging

- [x] Java 21 runtime and Maven Wrapper versions are pinned and locally verified.
- [ ] All Flyway migrations validate on a clean PostgreSQL database and on the previous release schema.
- [x] Runtime manifests are persisted on the report task and database-immutable after Preflight.
- [ ] Secrets are injected by the deployment environment; no credentials, tokens or provider keys are in artifacts.
- [ ] Authentication, tenant isolation, authorization and idempotency tests pass for every write endpoint.
- [ ] DOCX parsing rejects XXE, zip bombs, path traversal, oversized archives and invalid media bytes.
- [x] Runtime execution uses the configured sandbox policy; host-process execution is disabled in production and the local Docker integration test verifies UID/capability/rootfs restrictions. CI/staging evidence is still required.
- [ ] Java dependency/CVE scan has no unresolved critical/high findings or an approved time-bound exception.
- [x] `mvnw -Pquality verify`, UI build and dependency audit pass locally on Java 21.

## P1 - must pass before production canary

- [x] Testcontainers integration suite covers PostgreSQL, MinIO, Redis and the runtime adapter where available (`mvn -Pintegration verify`) locally; CI evidence is still required.
- [x] Docker lifecycle evidence covers distinct Installer, Execution and Cleanup calls sharing a task-scoped dependency volume.
- [ ] DOCX upload -> parse -> plan -> execute -> review -> export passes end to end with a real object store.
- [ ] Scheduler lease recovery, outbox replay, retry exhaustion and duplicate delivery are verified under restart.
- [ ] Concurrent task creation, duplicate idempotency keys and cross-tenant access attempts are verified.
- [ ] Load tests establish limits for upload size, task concurrency, SSE subscribers, LLM budgets and export size.
- [ ] Metrics, structured logs, trace/request IDs, dashboards and alerts are connected to the production observability stack.

## P1 - operations and recovery

- [ ] PostgreSQL and object storage backup jobs are configured and a restore drill has been recorded (runbook: `docs/operations/backup-restore.md`).
- [ ] Database migration rollback/forward procedures and application rollback procedures are documented.
- [ ] Alert ownership, on-call escalation, incident severity and audit-log retention are documented.
- [ ] Rate limits, quotas, retention windows and worker concurrency are configured per environment.
- [ ] Staging smoke test passes with production-like authentication, storage and LLM provider settings.

## Canary and release evidence

- [ ] Canary starts with the production profile and passes readiness/liveness checks.
- [ ] Error rate, latency, scheduler backlog, object-integrity failures and LLM/tool failures remain within thresholds.
- [ ] Rollback command has been tested and does not destroy durable task, workflow or object metadata.
- [ ] Release version, migration versions, artifact checksums and test reports are archived.

## Current status

The repository has a verified P0 build/toolchain baseline, secure parser and
runtime boundaries, durable scheduler/outbox behavior, and review-gated export.
The real local HTTP smoke has verified registration/JWT, MinIO upload, Apache
POI parsing and PostgreSQL document persistence. The planner-to-export portion
remains open until a freshly rotated LLM secret is injected. The Testcontainers
suite is implemented and passes locally on the Docker Desktop Linux engine with
digest-pinned images; CI evidence is still required. The Java dependency/CVE scan, recovery/load evidence,
production observability and staging/canary drills remain blockers.
The local PostgreSQL/MinIO SSH tunnel is development infrastructure only.
