# Portfolio Risk Register

| ID | Risk | Impact | Likelihood | Owner | Mitigation | Trigger/expiry |
| --- | --- | --- | --- | --- | --- | --- |
| R-001 | Provider credential exposed in chat | Account/data compromise | High | Security | Rotate key, inject only through secret manager, scan history/artifacts | Immediately; no authenticated smoke until rotated |
| R-002 | Live task events are process-local | User loses progress after refresh/restart | High | Backend | Durable event repository, bounded history API, reconnect from authoritative state | NEXUS-101 accepted |
| R-003 | LLM/runtime failure blocks report export | Incomplete report or confusing UX | Medium | AI/QA | Fail closed, explain stage/error, retry only transient faults, E2E evidence | Credentialed E2E passed |
| R-004 | Cross-tenant resource access | Confidentiality breach | Medium | Security/Backend | Composite ownership predicates, negative integration tests, review gate | Every endpoint contract change |
| R-005 | Dependency/CVE evidence unavailable | Unmeasured supply-chain risk | Medium | Security/DevOps | Warmed CI scan, SARIF archive, time-bound exceptions | Before staging |
| R-006 | Backup restore not rehearsed | Prolonged outage/data loss | Medium | SRE | Isolated PostgreSQL/MinIO restore drill and checksum evidence | Before production canary |
| R-007 | Parallel agents create conflicting contracts | Rework or hidden coupling | Medium | CTO/PM | Task ownership, architecture gate, independent review, no shared-file edits | Every parallel wave |
| R-008 | Shell app integration leaks capabilities | Host or cross-app compromise | Low/Medium | Desktop/Security | Manifest allow-list, iframe isolation, postMessage validation and CSP | App manifest release |

Risk owners update this table after each iteration. A risk is not closed because
code exists; it closes only with the verification evidence named in mitigation.
