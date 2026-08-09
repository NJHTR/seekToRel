# AgentGit Protocol

AgentGit is a control plane over Git, not a replacement for Git. Git stores code and small structured manifests; the ledger stores Agent actions and decisions; object storage stores large artifacts.

## Required identity

Every run has `tenant_id`, `task_id`, `agent_id`, `agent_version`, `model_version`, `sandbox_id`, `input_snapshot`, `parent_run_ids`, `policy_version`, and `trace_id`.

## Branches and paths

Use scoped branches such as `source/doc-123`, `review/doc-123/pass-1`, `review/doc-123/pass-2`, `review/doc-123/reentry`, `plan/doc-123/v3`, `agent/doc-123/q1/run-001`, and `release/doc-123/v1`.

Keep ownership clear: each Agent writes only its run directory and its evaluation/response files. Shared canonical files change through a merge request and workflow gate.

## Events

Record `PRECHECK_COMPLETED`, `AGENT_STARTED`, `COMMAND_STARTED`, `CHECKPOINT_CREATED`, `EVALUATION_WRITTEN`, `REVIEW_REQUESTED`, `APPROVED`, `REJECTED`, `RECOVERY_REQUESTED`, `ARTIFACT_PUBLISHED`, `MERGED`, `ROLLED_BACK`, and `RUN_FINISHED`.

## Merge rules

An Agent result is never automatically a business-state transition. A merge request must reference input hashes, evaluation packet, tests, policy result, and reviewer decision. Workflow Gate validates those references before merging.

## Collaboration

Agents communicate through versioned request/response/decision files and an event stream. Chat may explain intent but cannot be the source of truth. Conflicts are explicit objects requiring a reviewer; last-write-wins is forbidden for canonical results.

## Large artifacts

Logs, datasets, model files, images, DOCX, and PDFs live in object storage. Git records immutable URI, media type, size, checksum, retention class, and producing run.
