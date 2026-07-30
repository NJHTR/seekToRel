# ReportAI Production Release Checklist

This checklist defines the evidence required before a production release. A green Maven build alone is not a
production approval: the release must also pass integration, security, resilience and operational checks.

## Release gates

### P0 — must pass before staging

- [ ] Java 21 runtime and Maven Wrapper versions are pinned and verified.
- [ ] All Flyway migrations validate on a clean PostgreSQL database and on the previous release schema.
- [ ] Secrets are injected by the deployment environment; no credentials, tokens or provider keys are in artifacts.
- [ ] Authentication, tenant isolation, authorization and idempotency tests pass for every write endpoint.
- [ ] DOCX parsing rejects XXE, zip bombs, path traversal, oversized archives and invalid media bytes.
- [ ] Runtime execution uses the configured sandbox policy; host-process execution is disabled in production.
- [ ] Java dependency/CVE scan has no unresolved critical/high findings or has an approved time-bound exception.
- [ ] `mvnw -Pquality verify`, UI build and dependency audit pass.

### P1 — must pass before production canary

- [ ] Testcontainers integration suite covers PostgreSQL, MinIO, Redis and the runtime adapter where available (`mvn -Pintegration verify`).
- [ ] DOCX upload → parse → plan → execute → review → export passes end to end with a real object store.
- [ ] Scheduler lease recovery, outbox replay, retry exhaustion and duplicate delivery are verified under restart.
- [ ] Concurrent task creation, duplicate idempotency keys and cross-tenant access attempts are verified.
- [ ] Load tests establish limits for upload size, task concurrency, SSE subscribers, LLM budgets and export size.
- [ ] Metrics, structured logs, trace/request IDs, dashboards and alerts are connected to the production observability stack.

### P1 — operations and recovery

- [ ] PostgreSQL and object storage backup jobs are configured and a restore drill has been recorded (runbook: `docs/operations/backup-restore.md`).
- [ ] Database migration rollback/forward procedures and application rollback procedures are documented.
- [ ] Alert ownership, on-call escalation, incident severity and audit-log retention are documented.
- [ ] Rate limits, quotas, retention windows and worker concurrency are configured per environment.
- [ ] Staging smoke test passes with production-like authentication, storage and LLM provider settings.

### Canary and release evidence

- [ ] Canary starts with the production profile and passes readiness/liveness checks.
- [ ] Error rate, latency, scheduler backlog, object-integrity failures and LLM/tool failures remain within thresholds.
- [ ] Rollback command has been tested and does not destroy durable task, workflow or object metadata.
- [ ] Release version, migration versions, artifact checksums and test reports are archived.

## Current status

The repository currently has the P0 build/toolchain baseline, secure parser/runtime boundaries, durable scheduler and
review-gated export. Testcontainers integration, Java dependency/CVE scanning, operational recovery evidence, load/chaos
testing and production observability are still release blockers. The local PostgreSQL/MinIO SSH tunnel is suitable for
development verification, not itself a production deployment.
