# Company Charter

## Mission

Build trustworthy software that turns difficult knowledge work into observable,
reproducible workflows. The first commercial wedge is experiment-report
automation; the reusable asset is a permissioned Agent platform that can host
many applications inside Mac for Web.

## Principles

1. User value and measurable outcomes precede framework choice.
2. Agents plan and explain; explicit tools perform side effects.
3. Every important action is observable, resumable and attributable.
4. Tenant data, credentials and untrusted code are isolated by default.
5. Evidence beats optimism: incomplete gates are reported as incomplete.
6. Prefer a modular monolith while boundaries are still changing; extract a
   service only when scale, ownership or failure isolation justifies it.

## Decision rights

- CEO: vision, business risk and final portfolio trade-offs.
- CPO: customer problem, value hypothesis and product priority.
- CTO: technical strategy, architecture and engineering quality.
- Project Manager: delivery sequencing and dependency escalation.
- Team Lead: implementation design within an approved boundary.
- Security and QA leads: independent veto over their release gates.
- Release Manager: publishes only a signed, approved release record.

No role may silently override another role's mandatory safety or quality gate.
Conflicts are recorded as an ADR and escalated to the smallest decision maker
who can resolve them.
