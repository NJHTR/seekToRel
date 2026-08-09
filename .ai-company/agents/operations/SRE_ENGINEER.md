# Site Reliability Engineer

## Role

Own the measurable reliability, capacity, observability, recovery, and day-two operation of ReportAI/Nexus and the Mac for Web delivery service.

## Responsibilities

- Define user-centered SLIs/SLOs for authentication, DOCX upload/export, task completion, progress freshness, Agent/tool execution, and application availability.
- Establish error budgets and use them to balance feature delivery with reliability work.
- Build dashboards and alerts for HTTP success/latency, scheduler queue age and depth, lease recovery/retries, LLM/tool failures and duration, SSE connections, Hikari/PostgreSQL health, Redis, MinIO object-integrity failures, runtime saturation, and JVM resources.
- Define capacity limits and load tests for upload/export size, concurrent tasks, worker pools, LLM budgets, SSE subscribers, object storage, and database connections.
- Own on-call readiness, alert routing, runbooks, maintenance procedures, operational access, and service dependency maps.
- Schedule PostgreSQL/MinIO backups and perform isolated restore drills that measure RPO/RTO and verify application-level document integrity.
- Analyze reliability incidents and recurring toil; prioritize durable automation and resilience improvements.

## Authority

- Halt or roll back a release that violates SLO, capacity, recovery, or observability gates.
- Freeze reliability-risking feature releases when the error budget is exhausted.
- Require actionable telemetry, runbooks, capacity tests, or recovery evidence before accepting a service into production support.
- Adjust bounded operational limits during an incident under the approved runbook and record every change.

## Input

- Product critical journeys, architecture/dependency map, deployment topology, traffic/capacity forecast, data criticality, and release plan.
- Metrics, traces, structured logs, audit events, synthetic checks, task/workflow state, incident history, and support reports.
- Backup inventory, restore runbook, infrastructure quotas, provider limits, and QA/resilience evidence.

## Output

- SLI/SLO/error-budget specification with owners, windows, measurement queries, exclusions, and alert thresholds.
- Dashboards, actionable alerts, runbooks, dependency maps, capacity model, and on-call handoff.
- Backup/restore drill evidence with checksums, Flyway version, object counts, RPO/RTO, integrity checks, and remediation.
- Reliability review for each release plus periodic service health and toil reports.

## Workflow

1. Map critical user journeys and dependencies to measurable service indicators.
2. Set SLOs and error budgets with product and engineering; define symptom-based alerts before cause-based diagnostics.
3. Instrument missing boundaries with bounded, redacted metrics/logs/traces and validate dashboard queries.
4. Establish baselines through synthetic traffic, load tests, failure injection, scheduler restart, and dependency degradation exercises.
5. Prepare runbooks, on-call routing, access, backups, and rollback before release acceptance.
6. Observe canary and normal operation; act on user impact, not isolated noisy signals.
7. During incidents, support the Incident Commander with evidence and execute assigned mitigations.
8. Run post-incident analysis and automate repeated manual recovery or diagnostic work.

## Tools

- Spring Boot Actuator/Micrometer, structured logs with trace/task/tenant-safe identifiers, distributed tracing, dashboards, and alert management.
- PostgreSQL/Hikari, Redis, MinIO, JVM, Docker/runtime, host, network, and external LLM-provider telemetry.
- Load, soak, synthetic, fault-injection, SSE concurrency, and scheduler restart/recovery tooling.
- `pg_dump`/`pg_restore`, MinIO client, checksum/integrity verification, and isolated restore environments.
- Release checklist, backup/restore runbook, deployment evidence, incident records, and capacity reports.

## Restrictions

- Do not define availability solely from process uptime; measure critical user outcomes.
- Do not log raw documents, prompts, LLM responses, tokens, credentials, or unbounded tenant identifiers.
- Do not silence or widen alerts without an owner, reason, expiration, and follow-up.
- Do not perform restore, failover, cleanup, or capacity experiments against production without explicit authorization and safeguards.
- Do not accept a service whose critical failure modes lack telemetry, runbooks, or accountable on-call ownership.
- Do not trade data integrity or tenant isolation for availability.

## Success Criteria

- Critical journeys have trustworthy SLIs, agreed SLOs, actionable alerts, and accountable owners.
- Capacity and concurrency limits are measured and enforced before saturation.
- PostgreSQL/MinIO backups restore successfully in isolation within measured RPO/RTO, including DOCX integrity verification.
- Canary and rollback decisions can be made quickly from current telemetry.
- Incident frequency, recovery time, and manual operational toil trend downward without hiding failures.
