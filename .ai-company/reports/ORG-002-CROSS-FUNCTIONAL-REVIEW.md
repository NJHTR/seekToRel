# ORG-002 Cross-functional delivery review

**Review date:** 2026-08-06  
**Decision:** Approved for the first implementation wave; product release is not
approved by this record.  NEXUS-101 and NEXUS-102 remain subject to their own
architecture, code, QA and security gates.

## Named ownership

| Responsibility | Owner role | Authority | Current deliverable |
| --- | --- | --- | --- |
| Product value and scope | CPO / AI Product Manager | Accepts problem, outcome metric and non-goals | Nexus Observatory brief |
| Milestones and dependencies | Program Manager / Project Manager | Sequences work and records blockers | Task board and risk register |
| System boundary and contracts | Chief Architect / Backend Architect | Approves persistence, API and tenant boundaries | NEXUS-101/102 design |
| UI behavior and accessibility | Frontend Architect / UX Product Manager | Approves responsive states and recovery UX | Observatory acceptance flows |
| Backend implementation | Backend Team Lead | Owns scheduler, application and storage changes | NEXUS-101 implementation |
| Frontend implementation | Frontend Team Lead | Owns Nexus workspace and browser evidence | NEXUS-102 implementation |
| AI/runtime safety | AI Architect / AI Team Lead | Owns provider, tool and sandbox constraints | Existing ReportAI runtime gates |
| Quality acceptance | QA Manager / Test Engineer | Blocks merge on missing regression evidence | Unit, integration and browser checks |
| Security acceptance | Security Architect / Security Tester | Blocks release on tenant, secret or supply-chain risk | Threat and dependency review |
| Release and operations | Release Manager / SRE Engineer | Approves staging, rollback and recovery evidence | Production checklist |

## Review decisions

1. PostgreSQL is the authoritative source for task progress history; live SSE
   is an adapter and may not be presented as an audit store.
2. Event payloads are user-facing operational facts only. Prompts, raw model
   responses, document text, generated source, credentials and stack traces are
   outside the contract.
3. All history reads and task summaries are tenant-scoped, bounded and
   keyset-paginated. A missing or unauthorized task has one generic response.
4. The frontend must reconcile durable history before or during SSE reconnect,
   deduplicate by the server event identity, and expose truthful loading,
   failure, retry and export states.
5. No NEXUS item can be marked done from a unit build alone. The acceptance
   record must include the relevant PostgreSQL/MinIO/Redis/Docker evidence when
   infrastructure is required.

## Exit evidence for this review

- Product brief and architecture design exist and describe scope, non-goals,
  tenant controls and acceptance scenarios.
- Ownership is explicit for product, architecture, implementation, QA,
  security and release decisions.
- The task board carries the two implementation items and their external
  release blockers.
- The release checklist remains the authority for production approval; this
  review does not waive the credentialed LLM, CI CVE, recovery, backup or
  observability gates.

## Open follow-ups

- QA reruns the final combined diff after the backend and frontend workstreams
  report their verification results.
- Security reviews the final public event schema and checks dependency/CVE
  evidence in CI.
- Release operations record backup/restore and staging rollback drills before
  any production deployment.
