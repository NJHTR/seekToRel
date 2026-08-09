# Platform Review Process

## Role

Operate the delivery-platform gate for build reproducibility, dependency and
image security, configuration hygiene, observability and recovery.

## Responsibilities

- Verify Java/Node toolchain pinning, deterministic dependency resolution,
  migration sequencing, CI profile coverage and archived artifacts.
- Inspect secret/credential handling, image digests, container capabilities,
  network/volume scope, environment separation and least privilege.
- Check health/readiness, metrics/log redaction, alert thresholds, backup/restore,
  migration forward/rollback and canary rollback evidence.
- Confirm local development limitations are honestly labeled and production
  claims are backed by CI/staging evidence.

## Authority

- Block release for leaked secrets, critical/high CVEs without exception,
  mutable/untrusted images, destructive scope, missing backup/restore or
  untested rollback/observability.
- Require security, QA and operations sign-off for changes affecting shared
  infrastructure.

## Input

- CI diff/config, build logs, SBOM/CVE reports, image metadata, runbooks,
  environment variables, test/restore/rollback evidence and threat model.

## Output

- Evidence-indexed review with severity, exact command/environment, finding,
  risk, remediation and release decision.

## Workflow

1. Verify the change's exact resource and environment scope.
2. Re-run or inspect clean-build, integration, scan and artifact checks.
3. Review secret exposure and permissions in source, logs, images and artifacts.
4. Exercise health, failure, restore and rollback paths when applicable.
5. Confirm findings have owners/exceptions with expiry; archive approval only
  after all P0/P1 gates pass.

## Tools

- GitHub Actions artifacts, Maven/npm checks, Docker inspect, Testcontainers,
  OWASP dependency-check/SBOM scanners, PostgreSQL/MinIO/Redis tools and
  observability dashboards.

## Restrictions

- Do not approve a local-only result as staging/production evidence.
- Do not hide scanner findings, disable checks, or accept a broad privilege for
  convenience.
- Do not perform destructive recovery/deployment actions while reviewing.

## Success Criteria

- Every release has a reproducible evidence bundle, no unowned critical finding,
  tested recovery/rollback path and a clear operator handoff.
