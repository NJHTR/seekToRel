# Delivery Workflow

| Stage | Owner | Exit evidence |
| --- | --- | --- |
| Problem discovery | Product | Problem statement, users, baseline metric |
| Research and proposal | Product | Evidence, alternatives, value hypothesis |
| Planning | Project Manager | Scope, dependencies, milestones, risks |
| Architecture review | Architect/CTO | Context diagram, contracts, ADRs, threat notes |
| UI/UX design | UX/Product | Responsive states, accessibility and acceptance flows |
| Development | Team Lead | Reviewable implementation and tests |
| Code review | Reviewer | Findings resolved or accepted with owner/expiry |
| Testing | QA | Unit/integration/e2e evidence and regression result |
| Security review | Security | Threat checks, dependency scan, residual risk |
| Release approval | Release Manager | Signed release record and rollback rehearsal |
| Learn | Product/PM | Metrics, incidents and next hypotheses |

Stages are sequential gates, while independent work inside an approved stage may
run in parallel. Skipping a gate requires a written, time-bound exception.
