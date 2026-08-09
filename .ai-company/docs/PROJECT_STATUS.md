# Company Project Status

Updated: 2026-08-06

## Operating system

Status: **Phase 0 bootstrap and first cross-functional review complete**. Role
contracts, governance, named ownership and the first implementation-wave
decisions are versioned alongside the repository. Product release gates remain
independent and are not waived by the organization review.

## Portfolio snapshot

| Project | State | Evidence | Next gate |
| --- | --- | --- | --- |
| Mac for Web | Running baseline | Nexus frontend production build and shell browser checks | Application manifest and app health contract |
| Nexus/ReportAI | Pilot baseline | Java 21 quality/integration evidence, local auth and storage smoke | Credentialed DOCX upload-to-export and durable event history |
| Store embedded app | Portfolio integration | Built and copied by Nexus pipeline | Product brief before feature changes |
| Reddit embedded app | Portfolio integration | Responsive default/maximized checks | Product brief before feature changes |
| Portfolio app | Reference surface | Existing React project | Decide whether to embed or keep standalone |

## Known release blockers

The source of truth is `.ai/PROJECT_STATUS.md` plus
`docs/production-release-checklist.md`. The short list is: authenticated LLM
end-to-end evidence, CI CVE evidence, concurrency/recovery/load tests, backup
restore drill, production observability and staging canary/rollback.

## Operating metrics to add

- lead time from approved task to merged change;
- escaped P0/P1 defects and security findings;
- task completion/export success rate and median time;
- Agent tool-call rejection, retry and human-intervention rates;
- shell app launch/error rate and per-app adoption.

## Active delivery wave

`NEXUS-101` (durable task history/query) and `NEXUS-102` (Agent Observatory)
are implemented and in review. QA and security have a combined evidence gate:
Docker-backed migration/restart/tenant tests, browser refresh/reconnect/export
states and the standard code/QA/security review must be archived before the
items move from `REVIEW` to `DONE`.
