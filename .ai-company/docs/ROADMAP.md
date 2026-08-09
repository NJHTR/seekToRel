# Company Roadmap

This is the portfolio-level roadmap. Product-level detail belongs under
`.ai-company/projects/<id>/` and code-level release blockers remain in the
repository's existing `.ai/` and `docs/` records.

## Phase 0 - operating system (current)

- [x] Define executive, product, delivery, architecture, engineering, quality,
  security and operations role contracts.
- [x] Define review, security, Git and release gates.
- [x] Create project template and status surfaces.
- [ ] Run the first cross-functional review and assign named work items.

## Phase 1 - trustworthy Nexus pilot

- [x] Secure DOCX ingestion, canonical document model and review-gated export.
- [x] Local authentication, tenant boundary, scheduler/outbox and live progress.
- [ ] Persist task event history and expose a paginated Agent Observatory.
- [ ] Verify authenticated LLM planner-to-export flow with a rotated secret.
- [ ] Add user-visible recovery, retry explanation and artifact provenance.

## Phase 2 - platform expansion

- [ ] Add reusable tool permission bundles and an auditable tool marketplace.
- [ ] Add OCR/search/browser tools behind SSRF, quota and privacy controls.
- [ ] Add workflow templates, pause/resume checkpoints and human approval steps.
- [ ] Add multi-provider evaluation, prompt/version experiments and cost budgets.
- [ ] Productize Evidence Lab: replayable Agent runs, regression datasets and quality gates.
- [ ] Align Agent events with MCP and OpenTelemetry GenAI semantics.

## Phase 3 - Mac for Web application ecosystem

- [x] Host Nexus, Store, Reddit, Weather and portfolio surfaces in isolated windows.
- [ ] Add an application manifest contract and per-app health/version display.
- [ ] Add future apps: Notes/knowledge, Computer/developer studio and an
  Automation Center, each with an independent product brief before coding.
- [ ] Add shell-level permissions, offline/error states and app telemetry.
- [ ] Define an app manifest and SDK so new applications do not require core-shell edits.

## Release principle

The platform is not production-ready until the evidence in
`docs/production-release-checklist.md` is complete. Percent complete is a
planning signal, never a release gate.
