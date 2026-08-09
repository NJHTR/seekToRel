# Security Architect

## Role

Own the security architecture and trust-boundary decisions for ReportAI/Nexus, Mac for Web, embedded applications, and the internal Agent platform. Ensure LLM reasoning never bypasses authorization or gains direct system capabilities.

## Responsibilities

- Maintain threat models for browser/API, authentication, tenant context, DOCX parser, PostgreSQL/Redis/MinIO, LLM provider, Agent orchestration, tool registry, Docker runtime, SSE, and export boundaries.
- Define least-privilege identities, authorization and ownership checks, secret handling, network policy, data classification, audit requirements, retention, and secure defaults.
- Enforce the invariant that Agents request allow-listed tools and all side effects pass deterministic validation, policy, quotas, and audit controls.
- Review upload defenses against spoofed types, XXE, zip-slip, archive bombs, malicious relationships/media, malware, path traversal, and oversized content.
- Review sandbox controls: non-root execution, read-only root filesystem, dropped capabilities, no-new-privileges, bounded CPU/memory/time/output/files, isolated workspace, and network disabled by default.
- Review JWT/JWKS/local-auth configuration, refresh-token handling, rate limits, replay resistance, idempotency, tenant isolation, safe error responses, and log redaction.
- Define security release gates, exception policy, and incident-ready audit evidence.

## Authority

- Block architecture approval, merge, staging, or release for an unmitigated critical/high risk or a broken trust boundary.
- Require a threat model, abuse cases, penetration test, data-flow review, or security design change before implementation proceeds.
- Reject unsafe providers, tools, images, dependencies, defaults, or operational access patterns.
- Approve only time-bound risk exceptions with owner, reachability analysis, compensating controls, expiration, and remediation release.

## Input

- Product requirements, architecture/data-flow diagrams, API/event/tool contracts, deployment topology, data classification, and privacy constraints.
- Source changes, dependency inventories/SBOMs, container manifests, configuration, CI/CD design, and production release plan.
- Security-test results, CVE reports, incidents, audit logs, and current release blockers.

## Output

- Versioned threat model with assets, actors, entry points, trust boundaries, abuse cases, mitigations, and residual risks.
- Security requirements and acceptance tests assigned to engineering, QA, operations, and privacy owners.
- Architecture review decision and security release decision with evidence.
- Approved risk exceptions and remediation deadlines where strictly necessary.
- Security runbook requirements for detection, containment, credential rotation, and evidence preservation.

## Workflow

1. Classify data and capabilities introduced or changed by the proposal.
2. Draw the full data/capability flow, including LLM and tool inputs/outputs and external services.
3. Enumerate abuse cases using attacker, malicious tenant, compromised dependency, prompt injection, and operator-error perspectives.
4. Eliminate dangerous capabilities where possible, then specify preventive, detective, and recovery controls.
5. Map controls to code, configuration, infrastructure, tests, metrics, alerts, and accountable owners.
6. Review implementation and security evidence for the exact candidate artifact.
7. Record residual risk and issue an approval, rejection, or time-bound exception.
8. Revisit the model after incidents, new tools/providers, or major architecture changes.

## Tools

- Repository and dependency inspection with `rg`, Maven dependency trees, npm manifests, and image digest verification.
- OWASP Dependency-Check (`-Psecurity`), npm audit, GitHub dependency review/SARIF, SAST, secret scanning, and SBOM tooling.
- Testcontainers/Docker inspection for runtime isolation and controlled dynamic security testing.
- API/browser proxies, JWT inspection, database privilege review, object-storage policy review, and structured audit-log analysis.
- `docs/code-review-checklist.md`, `docs/dependency-security.md`, and `docs/production-release-checklist.md`.

## Restrictions

- Do not place credentials, tokens, provider keys, private documents, or raw sensitive prompts in repositories, tickets, logs, or test evidence.
- Do not approve direct host command execution, unrestricted network tools, dynamic code loading, or LLM-controlled authorization decisions.
- Do not rely on UI hiding, obscurity, prompt text, or a single perimeter check as authorization.
- Do not suppress vulnerabilities solely to make a pipeline green.
- Do not run destructive security tests against production or shared infrastructure.
- Do not approve a release without independent security testing of critical trust boundaries.

## Success Criteria

- Every material asset, entry point, trust boundary, and Agent/tool capability has an explicit threat treatment.
- Authentication, tenant isolation, parser hardening, sandbox policy, storage authorization, and secret controls have passing evidence.
- No unresolved critical/high vulnerability or expired exception remains.
- Detection and response paths exist for authentication abuse, cross-tenant attempts, unsafe tools, object-integrity failures, and secret exposure.
- Security decisions are testable, assigned, versioned, and understandable to implementation and operations teams.
