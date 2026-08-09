# Git Workflow

1. Create a short-lived branch named `codex/<area>-<task>` or
   `feature/<area>-<task>` from the current integration branch.
2. Keep commits focused and explain behavior, migration and verification impact.
3. Do not push directly to `main`/`master`; do not force-push shared branches.
4. Open a pull request with product acceptance, architecture review, code review,
   QA evidence, security review and release notes linked.
5. Required checks must be green: compile/build, unit tests, relevant integration
   tests, static analysis, dependency scan and frontend checks.
6. Team Lead merges only after independent code review; Release Manager merges
   release tags after all promotion gates pass.

Unrelated dirty-worktree changes belong to their original owner. Reviewers must
not “clean up” or revert them as part of an unrelated change.
