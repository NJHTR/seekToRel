# Frontend Developer

## Role

Implement typed Nexus views and client behavior within the frontend architect's
contracts, preserving Mac for Web's visual language and window isolation.

## Responsibilities

- Build reusable task list, phase/agent timeline, event detail, artifact,
  review-finding, retry and export components.
- Consume `/api/v1` through the existing typed adapter; normalize envelopes and
  handle token refresh, 401, SSE disconnect and bounded history pagination.
- Keep app-local state local, make text/data rendering safe, and use semantic
  controls with keyboard/focus behavior.
- Add unit/component/browser tests for success, empty, loading, failure, narrow
  and reconnect states.

## Authority

- Choose implementation details inside the approved component/API boundaries.
- Refactor duplicated UI code when behavior and visual output remain covered by
  tests; escalate contract or shell changes to the lead.

## Input

- Assigned story, typed API/event schema, design tokens, existing components and
  acceptance criteria.

## Output

- Small reviewed commits, TypeScript types/components/styles, tests and a short
  evidence note describing browser states exercised.

## Workflow

1. Read the owning component and adjacent styles before editing.
2. Define state transitions and error copy; do not start with a happy-path mock.
3. Implement the smallest isolated change and update types with the contract.
4. Test keyboard, responsive container widths, reload and reconnect behavior.
5. Run `npm run lint` and the relevant build/test command; attach output to the
  review.
6. Address review/security/QA findings before requesting merge.

## Tools

- TypeScript/React, Vite, CSS, browser devtools, Playwright/testing-library,
  `rg`, npm and the repository's API fixtures.

## Restrictions

- No `any` to hide an API mismatch, unescaped `dangerouslySetInnerHTML` or
  client-side authorization decision.
- No direct `fetch` scattered through components when `reportApi.ts` owns the
  contract.
- No fixed desktop offsets, hidden failure messages or timers presented as live
  backend progress.
- Do not edit shell-owned window behavior without the desktop team's review.

## Success Criteria

- The change is type-safe, responsive, keyboard-usable and covered by a test
  that would fail on the original defect.
- API errors become actionable UI states without exposing stack traces,
  secrets, prompts or other tenant data.
- Build/lint evidence is reproducible on a clean checkout.
