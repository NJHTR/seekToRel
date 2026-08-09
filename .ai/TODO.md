# Active TODO

- [x] Replace the single-instance local authentication limiter with a pluggable Redis-backed distributed rate limit (`AuthAttemptGuard`, Lua fixed window); switch via `report-ai.auth.rate-limit.backend=redis`.
- [ ] Promote Redis/gateway-backed distributed rate limiting to the default behind a multi-replica deployment.
- [ ] Add email verification, password reset, account lock/unlock administration and auditable role management before public registration is enabled.
- [x] Add an environment-backed SMTP delivery port and optional adapter; raw mailbox credentials remain excluded.
- [x] Add physical orphan-object retention cleanup with an explicit retention policy.
- [ ] Add optional WebSocket adapter alongside SSE.
- [x] Add Maven wrapper, PMD/CPD static-analysis reports and an application JaCoCo line-coverage gate (the `quality` profile enforces Java 21/Maven and a 20% line baseline).
- [x] Add high-severity dependency review to pull-request CI.
- [ ] Validate OWASP Java dependency/CVE scanning on a warmed CI database; profile, CVSS 7.0 threshold, CI reports and exception policy are implemented.
- [x] Add and locally execute opt-in Testcontainers PostgreSQL/Redis/MinIO/Docker integration tests; CI execution evidence remains open.
- [x] Add scheduled snapshot compaction with bounded retries, Micrometer counters/timer, and externalized cadence settings.
- [ ] Add OCR/search/browser tools with SSRF and audit controls.
- [x] Validate structured LLM tool-call responses against declared tools, call budgets, JSON-schema basics and bounded arguments.
- [x] Add backup/restore, observability dashboards and production release checklist specification.
- [x] Pin Docker/Testcontainers/runtime images by digest.
- [x] Verify non-root user, dropped capabilities and runtime sandbox policy in the Docker integration path; CI evidence remains required.
- [ ] Inject the rotated LLM secret through a runtime-only hosted process environment and create a new authenticated DOCX task through planner, coder, runtime, review and export. Coder now defaults to low effort, 2,048 output tokens and a 90-second attempt; the previous live task is terminal by design.
- [x] Make runtime network access fail-closed, reject credential-shaped environment names, and require immutable image digests.
- [ ] Implement managed egress proxy/policy evaluation before enabling runtime network capabilities.
- [ ] Split runtime mounts into read-only input and read-write output before production sandbox release.

## Company operating system

- [x] Create `.ai-company` role organization and department contracts.
- [x] Create development, code-review, security, Git and release governance.
- [x] Create portfolio roadmap, status, task board, risk register and project templates.
- [x] Run the first cross-functional review of the company documents and assign named owners.
- [x] Implement the NEXUS-101 durable task-event history and bounded query API; production acceptance evidence remains open.
- [x] Implement the NEXUS-102 Agent Observatory task history/recovery UI; browser and container acceptance evidence remains open.

## Current release-gate work

- [ ] Execute the Docker-enabled PostgreSQL/MinIO event-history integration suite and archive the migration, restart, duplicate and tenant-isolation evidence.
- [ ] Run authenticated Nexus browser tests for refresh, history replay, SSE reconnect and export; the local Reddit CRA bundle is blocked by Windows `spawn EPERM`.
- [ ] Validate the warmed OWASP NVD cache, backup/restore drill, scheduler recovery/load test and staging canary rollback.
- [x] Prevent concurrent Nexus refresh-token rotation, clear stale sessions on 401, and cool down refresh attempts after 429.
- [x] Diagnose local `LLM_REQUEST_INVALID` before provider I/O and prevent deterministic validation failures from consuming scheduler retries.
- [x] Fix validator false positive that rejected `max_completion_tokens` as a credential option.
- [x] Bound the default Coder completion budget and effort to reduce upstream gateway timeouts; keep the 16,384 safety ceiling configurable.
