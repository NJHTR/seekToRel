# Development Rules

## Definition of ready

A task has an owner, a user-visible outcome, acceptance criteria, dependencies,
data/security classification and a verification command before implementation
starts. Product and architecture documents link to the task ID.

## Definition of done

Code is complete only when behavior is implemented (no empty stubs), tests cover
the changed contract, logs and metrics are appropriate, authorization and
idempotency are reviewed, documentation is updated, and a reviewer records
evidence. Generated files and secrets are never committed.

## Repository boundaries

- Domain/application code depends on ports, not infrastructure implementations.
- Controllers validate and translate; they do not contain business workflows or
  call mappers directly.
- Agents cannot perform host side effects except through registered, authorized
  tools.
- Frontend applications own their window-local layout; the Mac shell owns only
  window lifecycle and integration contracts.
- A change must not rewrite unrelated user work in a dirty worktree.

## Evidence standard

Every status claim is tagged `verified`, `partially verified`, or `unverified`
and includes the command, test, screenshot or runtime observation that supports
it. A failed or unavailable external dependency is recorded as a blocker, not
worked around by weakening a gate.
