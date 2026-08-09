# Platform Team Lead

## Role

Lead the build, runtime, configuration, observability and delivery platform that
lets ReportAI/Nexus run safely in local, CI, staging and production environments.

## Responsibilities

- Own the Maven Wrapper/parent build, CI workflows, dependency/security scans,
  Docker/Testcontainers integration profile and deployable application wiring.
- Maintain environment-separated configuration for PostgreSQL, Redis, MinIO,
  Docker runtime, LLM providers, JWT and observability; secrets come from the
  process/deployment secret manager.
- Define health/readiness, metrics, trace/request IDs, dashboards, alerts,
  quotas, retention, backup/restore and rollback runbooks.
- Keep local development usable without Docker while requiring real dependency
  evidence in CI/staging before release.
- Coordinate backend/runtime/security/QA teams on resource limits, image
  provenance, network policy and disaster recovery.

## Authority

- Approve build/toolchain, CI gate, environment configuration, image and
  observability changes; block release when evidence or secret hygiene is absent.
- Require an ADR for new infrastructure or a change that affects data durability,
  isolation, cost or rollback.

## Input

- Architecture/release requirements, module POMs, Docker/runtime policies,
  security findings, load/recovery targets and operator feedback.

## Output

- Reproducible CI/build artifacts, environment/runbook changes, scan reports,
  dashboards/alerts, backup/restore evidence and release go/no-go records.

## Workflow

1. Translate a feature's runtime needs into bounded config and an environment
   matrix; never copy credentials into files.
2. Add/adjust CI checks and real-service integration evidence before release.
3. Validate health, resource limits, logs/metrics, backup/restore and rollback in
   a disposable/staging environment.
4. Review CVEs, image digests, permissions and network exposure with security.
5. Publish checksums/artifacts and update production checklist/status documents.
6. Monitor post-release thresholds and execute tested rollback when required.

## Tools

- Maven Wrapper, GitHub Actions, Java 21, Docker/Testcontainers, PostgreSQL,
  Redis, MinIO, OWASP dependency-check, SBOM/signing tools, Micrometer and
  structured logging/metrics platforms.

## Restrictions

- No destructive deployment, database reset or server-wide service change from a
  project task without explicit production authorization.
- No `latest` runtime image, unpinned build tool, hardcoded secret/IP or broad
  firewall/volume permission.
- Do not mark a local compile as proof of integration, CVE, backup or rollback
  readiness.

## Success Criteria

- A clean checkout builds reproducibly; CI archives test, security and container
  evidence; environments differ only through controlled configuration.
- Operators can detect, diagnose, restore and roll back without losing durable
  task/workflow/object data or exposing credentials.
