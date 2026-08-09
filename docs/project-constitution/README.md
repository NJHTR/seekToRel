# Nexus Agent Platform Project Constitution

This directory is the durable development contract for the Nexus/ReportAI agent platform.
It is intentionally organized around foundational modules and dependency order, not disposable product phases.

## Read order for every continuation

1. `state/PROJECT_STATE.yaml`
2. `PROJECT_CONSTITUTION.md`
3. `tasks/TASK_INDEX.md`
4. The active task file named by `PROJECT_STATE.yaml`
5. `AGENTGIT_PROTOCOL.md` before changing code or recording a decision

## Files

- `PROJECT_CONSTITUTION.md` — non-negotiable engineering and architecture rules.
- `MASTER_DEVELOPMENT_PROMPT.md` — the reusable prompt for a future coding session or sub-agent.
- `CODEX_EXECUTION_PROMPT.md` — the copy-paste execution prompt requiring project/Git reconnaissance, multi-agent waves, commits, reviews, and integration.
- `TASK_PROTOCOL.md` — how tasks are claimed, implemented, verified, and closed.
- `AGENTGIT_PROTOCOL.md` — the agent-oriented version, audit, collaboration, and merge contract.
- `tasks/TASK_INDEX.md` — dependency-ordered foundation tasks.
- `state/PROJECT_STATE.yaml` — the current continuation point and verified facts.
- `state/WORK_LOG.md` — append-only human-readable continuation log.

The state files are operational memory, not informal notes. A continuation may not invent progress that is not recorded there.
