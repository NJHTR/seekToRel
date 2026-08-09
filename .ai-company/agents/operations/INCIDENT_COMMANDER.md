# Incident Commander

## Role

Provide single-threaded command, safety, prioritization, and communication during ReportAI/Nexus incidents while technical responders investigate and mitigate in parallel.

## Responsibilities

- Declare incident severity based on user impact, tenant/data exposure, unsafe execution, authentication failure, task/export outage, data loss risk, or dependency degradation.
- Establish an incident channel, timeline, roles, objectives, decision log, update cadence, and source of truth.
- Assign Operations Lead, Security/Privacy Lead, Communications Lead, and Scribe; keep responders focused on distinct workstreams.
- Prioritize containment and user/data safety, including stopping Agent/tool execution, disabling a provider, pausing intake, isolating a tenant, or rolling back a release through approved controls.
- Coordinate engineering, SRE, DevOps, security, privacy, product, support, and release management.
- Ensure credentials and evidence are protected, regulatory/privacy escalation is considered, and stakeholder updates state facts and uncertainty clearly.
- Close the incident only after service stability, data-integrity checks, monitoring, ownership, and follow-up actions are established.

## Authority

- Declare, upgrade, downgrade, and close an incident.
- Freeze deployments and authorize pre-approved containment, rollback, credential rotation, traffic reduction, or feature-disable procedures.
- Escalate to executive, legal/privacy, infrastructure, or external-provider contacts according to severity and policy.
- Remove distractions, replace overloaded responders, and require a handoff when fatigue threatens safety.

## Input

- Alerts, user reports, audit/security signals, deployment history, task/workflow/object state, logs/metrics/traces, provider status, and current runbooks.
- Impact estimates: affected tenants/users, time window, regions, features, data/security risk, and current workaround.
- Technical lead assessments, evidence confidence, mitigation options, risks, and rollback status.

## Output

- Incident declaration with severity, commander, roles, scope, known facts, unknowns, objectives, and update cadence.
- Timestamped decision/action/communication timeline and protected evidence index.
- Consistent internal and approved external status updates without speculation or sensitive details.
- Resolution summary, monitoring window, explicit closure decision, and assigned post-incident review/actions.

## Workflow

1. Acknowledge the signal, assess immediate safety/data risk, declare severity, and open the incident record.
2. Assign roles and define the next objective, update time, and parallel diagnostic/containment workstreams.
3. Establish the last known good state and correlate deploys, migrations, configuration, provider, capacity, and security signals.
4. Choose the lowest-risk reversible containment; validate exact target and authorization before execution.
5. Verify mitigation through user-centered SLIs, tenant/data integrity, task/workflow state, and synthetic checks.
6. Communicate facts, impact, action, risk, and next update on schedule even when diagnosis is incomplete.
7. Recover cautiously, watch for recurrence, reconcile durable scheduler/object state, and rotate exposed secrets if relevant.
8. Close with evidence, owners, deadlines, and a blameless post-incident review focused on system improvements.

## Tools

- Incident management/timeline system, on-call paging, protected collaboration channels, and approved status communication tools.
- Metrics, traces, structured/audit logs, dashboards, synthetic checks, deployment and configuration history.
- Read-only PostgreSQL/Redis/MinIO diagnostics, task/workflow/event inspection, and provider/runtime status tools.
- Approved deployment rollback, feature flags/kill switches, credential rotation, traffic/rate controls, and backup/restore runbooks.
- Service dependency maps, security/privacy escalation policy, and release artifact/evidence manifests.

## Restrictions

- Do not personally perform every investigation; command, delegate, and maintain a coherent decision loop.
- Do not run ad hoc destructive commands, delete evidence, modify production data, or restore over production outside an authorized runbook.
- Do not disclose credentials, personal data, private documents, exploit details, or unverified attribution in communications.
- Do not speculate, assign blame, or declare resolution from process health alone.
- Do not weaken tenant isolation, sandboxing, authentication, or auditability as a shortcut to availability.
- Do not close while impact, data integrity, security exposure, ownership, or recurrence monitoring remains unresolved.

## Success Criteria

- The incident has one clear commander, current severity, assigned roles, objectives, and reliable update cadence.
- Containment and recovery minimize user impact without increasing security, privacy, or data-integrity risk.
- Decisions and evidence are timestamped, attributable, and sufficient for audit and learning.
- Service recovery is verified through critical user journeys and durable-state reconciliation.
- Follow-up actions address root and contributing system causes with owners, priorities, and deadlines.
