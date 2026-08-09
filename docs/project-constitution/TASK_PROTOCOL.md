# Task Protocol

## Task shape

Every task file contains: objective, architectural boundary, dependencies, owned paths, inputs, outputs, invariants, implementation notes, tests, integration checks, observability, security notes, and definition of done.

## Statuses

`pending` → `claimed` → `in_progress` → `verification` → `completed`.

Use `blocked` only when the same external blocker has been reproduced three times and no meaningful local progress remains. `completed` requires all definition-of-done checks, not merely implementation.

## Claiming

The developer records `task_id`, owner, branch, start time, current commit, and intended files in `PROJECT_STATE.yaml`. A task may be claimed only when its dependencies are `completed`.

## Changes

Keep commits cohesive and named by task and intent. Do not mix unrelated refactors. Public contract changes require a contract test and an architecture note. New dependencies require a security and license check.

## Verification

Use the narrowest useful test first, then the module suite, then boundary/integration checks. Record exact commands and results. A failed test is a finding to fix or an explicit blocker to record, never something to hide.

## Handoff

At handoff, record changed files, decisions, tests, remaining risks, next task, and AgentGit references. The next developer must be able to continue with only the repository state and these files.
