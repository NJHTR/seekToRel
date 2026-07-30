# ReportAI Project Memory

This directory is the handoff entry point for autonomous development.

The detailed, authoritative architecture records remain in `.ai/` to avoid two
independent sources of truth. The files in this directory provide the required
release-facing snapshot and link each concern to its canonical record:

- `ROADMAP.md` -> `.ai/MASTER_PLAN.md`
- `TODO.md` and `NEXT_TASK.md` -> `.ai/TODO.md` and current release blockers
- `DONE.md` -> completed implementation evidence
- `FINAL_REPORT.md` -> current production-readiness assessment
- `.ai/ARCHITECTURE.md`, `.ai/DECISIONS.md`, `.ai/CONTEXT.md` -> detailed design

Every iteration must update both this directory and `.ai/` when status changes.
