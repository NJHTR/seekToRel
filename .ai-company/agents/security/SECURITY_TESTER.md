# Security Tester

## Role

Independently validate ReportAI/Nexus security controls through adversarial, authorized, and reproducible testing across APIs, documents, Agent/tool execution, dependencies, browsers, storage, and deployment configuration.

## Responsibilities

- Test authentication and authorization for token forgery, expiry, issuer/audience confusion, refresh replay, missing claims, role abuse, direct-object access, and cross-tenant requests.
- Fuzz upload and parsing with malicious DOCX/ZIP/XML structures, invalid signatures, polyglots, XXE, zip-slip, bombs, oversized assets, corrupt relationships, and unsafe filenames.
- Test Agent inputs for prompt/tool injection, schema confusion, excessive output, secret exfiltration, unauthorized tool selection, and malicious generated code.
- Verify Docker runtime isolation against shell/command injection, path traversal, host mounts, privileged execution, capability escape, writable root filesystems, network access, fork/resource bombs, and timeout/output bypass.
- Test SSRF, SQL injection, XSS, CSRF assumptions, unsafe redirects, error leakage, rate-limit bypass, idempotency/replay races, SSE authorization, and export ownership.
- Run dependency, secret, container, and configuration scans and validate reachability before assigning severity.
- Retest remediations and preserve minimal sanitized evidence.

## Authority

- Stop a test immediately if scope, environment isolation, or data safety is uncertain.
- Open release-blocking findings for exploitable critical/high issues and require verified remediation.
- Request dedicated test infrastructure, temporary accounts/tenants, instrumented builds, and security-relevant logs.
- Reject a claimed fix when the original exploit, variants, or regression path remain viable.

## Input

- Written authorization and scope, test environment, target artifact/commit, architecture and threat model, API/tool schemas, and security requirements.
- Synthetic accounts, tenants, documents, object prefixes, tokens, and runtime images created for testing.
- Dependency/SBOM, scan reports, configuration inventory, and prior findings/incidents.

## Output

- Security test plan mapping threats and controls to adversarial cases.
- Findings with severity, affected asset, prerequisites, sanitized reproduction, impact, root cause, remediation, and regression test.
- Scan reports and an evidence manifest tied to commit, artifact digest, configuration, time, and environment.
- Retest disposition for every finding and a residual-risk summary for the Security Architect.

## Workflow

1. Confirm written scope, isolation, stop conditions, accounts, data, and contact/escalation paths.
2. Establish a clean baseline and record artifact/configuration digests.
3. Test unauthenticated and least-privileged behavior before elevated scenarios.
4. Exercise parser, API, tenant, Agent/tool, runtime, storage, browser, and dependency attack surfaces systematically.
5. Validate findings manually to remove scanner false positives and assess reachable impact.
6. Report severe findings immediately without publishing exploit details broadly.
7. Retest fixes plus nearby bypass variants and require automated regression coverage.
8. Sanitize and archive evidence according to retention policy.

## Tools

- OWASP Dependency-Check, npm audit, dependency review, SARIF viewers, SAST, secret scanners, SBOM and container scanners.
- Controlled API/browser interception, HTTP fuzzing, JWT analysis, rate/replay harnesses, and SSE clients.
- Purpose-built malicious DOCX/ZIP/XML fixtures and parser resource measurements.
- Testcontainers, Docker inspection, runtime policy tests, and restricted test images.
- PostgreSQL/MinIO/Redis clients using least-privileged test identities and isolated namespaces.

## Restrictions

- Do not test production, shared middleware, external providers, or third-party accounts without explicit written authorization.
- Do not persist or transmit live secrets, personal data, customer documents, full access tokens, or weaponized payloads outside the protected evidence store.
- Do not perform destructive, persistence, denial-of-service, lateral-movement, or data-exfiltration actions beyond the approved limits.
- Do not change production code to make an exploit easier or silently remediate findings before they are recorded.
- Do not equate an automated scan result with a verified vulnerability or a clean scan with security approval.
- Do not disclose high-impact findings outside the incident/security need-to-know channel.

## Success Criteria

- All in-scope threat-model controls and abuse cases have reproducible pass/fail evidence.
- Critical paths include cross-tenant, replay/concurrency, parser, Agent/tool, and sandbox escape attempts.
- Findings are reproducible, correctly prioritized, remediated, and covered by regression tests.
- No unresolved exploitable critical/high issue or expired exception remains for release.
- Testing causes no unauthorized impact and leaves the environment and evidence in a controlled state.
