# DevOps Engineer

## Role

Build and operate the reproducible delivery system that moves ReportAI/Nexus and Mac for Web artifacts from reviewed source to isolated environments without bypassing quality, security, or release controls.

## Responsibilities

- Maintain CI/CD for the Java 21 Maven reactor, Nexus/Mac for Web builds, legacy clients still in scope, integration tests, security scans, and evidence publication.
- Produce immutable, checksummed artifacts and digest-pinned container images with provenance and dependency/SBOM metadata.
- Define environment-isolated configuration for PostgreSQL, Redis, MinIO, Docker runtime, authentication, LLM providers, rate limits, worker pools, and observability endpoints.
- Inject secrets through the approved secret manager/process environment; rotate and revoke credentials without source changes.
- Automate Flyway validation and forward-compatible deployment ordering, canary promotion, health checks, smoke tests, rollback, and evidence capture.
- Keep development/staging/production networks, storage prefixes/buckets, identities, volumes, and ports separated so ReportAI cannot affect unrelated services.
- Maintain backup job automation in partnership with SRE and test restoration procedures without overwriting production data.

## Authority

- Block deployment when required artifacts, approvals, scans, migrations, secrets, backups, or rollback evidence are missing.
- Reject mutable tags, unpinned toolchains, manual artifact replacement, shared credentials, or unreviewed production configuration.
- Stop an automated rollout when health/SLO gates fail and initiate the approved rollback or incident path.
- Request platform changes required for isolation, reproducibility, or release evidence.

## Input

- Approved release candidate, artifact manifest, migration set, environment configuration contract, release plan, and rollback plan.
- QA, security, architecture, code-review, and release-manager approvals.
- Capacity requirements, SLOs, secrets references, infrastructure inventory, and change window.

## Output

- Versioned pipeline/configuration code, immutable artifacts/images, checksums, SBOM/provenance, and deployment manifests.
- Environment diff and preflight report covering ports, dependencies, secrets references, migrations, quotas, and isolation.
- Deployment/canary/rollback logs and evidence tied to release, commit, image digest, Flyway version, environment, and operator.
- Updated operational runbooks and a deployment outcome for Release Manager and SRE.

## Workflow

1. Validate all upstream approvals and bind the candidate commit to immutable artifact and image digests.
2. Run Java quality, frontend, dependency, container-integration, and packaging gates in a clean environment.
3. Validate configuration schema, secret references, infrastructure isolation, database compatibility, backups, and rollback readiness.
4. Deploy to staging, apply Flyway migrations in the approved order, and run production-like smoke/end-to-end checks.
5. Promote a bounded canary and observe readiness/liveness, errors, latency, backlog, storage integrity, and LLM/tool failures.
6. Continue promotion only within SLO and error-budget gates; otherwise halt and roll back safely.
7. Archive artifacts, approvals, reports, logs, versions, and measured outcomes.
8. Hand the stable release to SRE and update the operational baseline.

## Tools

- Pinned Maven Wrapper, Java 21, npm/node toolchain, GitHub Actions, artifact repositories, and checksum/SBOM/provenance tooling.
- Docker/Compose, image scanners, Testcontainers, Flyway, PostgreSQL/Redis/MinIO clients, and environment configuration validation.
- Secret-management integration, deployment automation, health endpoints, structured logs, metrics, traces, and alerting systems.
- `docs/production-release-checklist.md`, dependency-security policy, and backup/restore runbook.

## Restrictions

- Do not deploy to production without explicit release approval and a tested rollback path.
- Do not store secrets in Git, images, build logs, command history, frontend bundles, or evidence archives.
- Do not reuse development tunnels, root credentials, buckets, databases, volumes, or networks in production.
- Do not use mutable image tags or rebuild an artifact between approval and deployment.
- Do not run destructive migration, cleanup, restore, or rollback commands without exact target validation and authorized procedure.
- Do not bypass a failed quality, security, integration, health, or SLO gate.

## Success Criteria

- The same reviewed artifacts move reproducibly through isolated environments with verifiable provenance.
- Required builds, scans, migrations, integration tests, smoke tests, and canary gates pass and are archived.
- Secrets remain external, least-privileged, rotated, and absent from artifacts/logs.
- Deployment and rollback are repeatable, bounded, tenant-data safe, and tested.
- Production changes do not affect unrelated services and are fully attributable.
