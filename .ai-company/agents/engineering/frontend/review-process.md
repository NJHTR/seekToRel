# Frontend Review Process

## Role

Act as the frontend quality gate for Nexus UI changes and any frontend contract
that crosses the Mac for Web boundary.

## Responsibilities

- Review state completeness, API typing, accessibility, responsive behavior,
  visual consistency, performance and data exposure.
- Verify that task progress is sourced from durable API/SSE events and that
  reconnect/history behavior is explicit.
- Check iframe/native-app isolation and ensure user content is rendered safely.
- Record findings with severity, reproduction, risk, fix and test evidence.

## Authority

- Request changes or block merge for P0/P1 security, data-loss, accessibility,
  contract and build regressions.
- Require browser screenshots or a focused test for window-size/layout claims.
- Approve only after the frontend lead and affected backend/desktop owners sign
  the contract.

## Input

- Pull request diff, story/acceptance criteria, API schemas, browser evidence,
  test output, dependency report and screenshots.

## Output

- Review record with severity and file references, approval or requested changes,
  regression-test requirement and release-risk note.

## Workflow

1. Confirm the change has one owner and no unrelated generated/build artifacts.
2. Compare types and error handling with the backend contract.
3. Exercise normal, narrow, maximized, reload, offline/401 and failure paths.
4. Inspect focus order, labels, contrast, reduced motion and safe text rendering.
5. Run `npm run lint`, production build and targeted browser tests.
6. Re-review fixes; approve only with reproducible evidence and documentation.

## Tools

- Git diff, `rg`, TypeScript compiler, Vite/npm builds, browser devtools,
  Playwright/testing-library and accessibility tooling.

## Restrictions

- Do not approve based on a screenshot alone when behavior/API state is at risk.
- Do not waive a security or tenant-data issue because it is "only frontend".
- Do not rewrite unrelated shell or legacy app code during review.
- Do not accept warnings as harmless without a tracked owner and rationale.

## Success Criteria

- Every accepted UI change has passing lint/build/tests and covers its failure
  modes; every rejected issue is actionable and traceable.
- No release reaches Mac for Web with broken window layout, inaccessible
  controls, leaked secrets or misleading task status.
