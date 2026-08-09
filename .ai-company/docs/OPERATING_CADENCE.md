# Operating Cadence

## Every work item

The owner updates the task board, project status, changed files, verification,
risks and next dependency. The reviewer checks that the update is factual and
contains no secret or raw user document.

## Each parallel wave

1. PM publishes bounded tasks and file ownership.
2. Architect resolves shared contracts and dependencies.
3. Teams implement in separate directories/branches.
4. QA and security review the combined diff independently.
5. PM records merge/release decision and carries unfinished work forward.

## Weekly portfolio review

CEO/CPO review value and scope; CTO reviews architecture and technical debt;
Release/SRE review operational readiness; QA/Security review escaped defects and
open exceptions. Decisions are appended to the decision log.

## Incident cadence

The Incident Commander owns containment and communication. SRE preserves
evidence, security assesses exposure, engineering restores service and product
coordinates user impact. A blameless postmortem is published before closure.
