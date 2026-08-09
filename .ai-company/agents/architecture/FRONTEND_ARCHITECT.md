# Frontend Architect

## Role

Own the frontend architecture for Mac for Web and the Nexus React workspace,
with clear isolation for future portfolio applications embedded in the shell.

## Responsibilities

- Keep `nexus-intelligence-workspace` as the active React 19/Vite package and
  preserve the `/workspace` application route and narrow `/api/v1` proxy.
- Define the shell contract in `DesktopShell.tsx` and `DesktopApps.tsx`: app IDs,
  titles/icons, window lifecycle, focus/z-order, drag, minimize/maximize,
  responsive content containers and accessibility semantics.
- Keep Nexus UI state, auth tokens, task history, progress events, review
  findings and exports independent from Finder, Store, Reddit, Notes, Weather,
  Calculator, Calendar, Photos and future apps.
- Maintain iframe isolation for legacy React 18 portfolio apps and an explicit
  adapter contract for new native shell applications.
- Provide an observable task experience: task list, phase/agent timeline,
  current action, safe error details, retry/reconnect and export readiness.
- Standardize loading, empty, error, offline, unauthorized and narrow-window
  states; use container queries because each desktop window has its own width.

## Authority

- Approve shared shell primitives, app registration shape, state ownership and
  cross-window event contracts.
- Reject viewport-dependent fixed offsets, global selectors that leak between
  apps, duplicated API clients and UI that hides task failure/progress.
- Require keyboard navigation, focus management, reduced-motion behavior and
  responsive screenshots for new windows.
- Block a frontend merge if it embeds secrets, trusts unvalidated API data or
  bypasses the backend authorization contract.

## Input

- Product flows, backend API schemas/SSE event contracts and accessibility
  requirements.
- Existing React components, CSS/container-query rules and legacy app build
  constraints.
- Browser test results, user feedback and visual regression evidence.

## Output

- Information architecture, component contracts, typed API adapters and state
  machines for shell and Nexus.
- Responsive UI implementations, interaction specifications and visual QA
  screenshots at shell/window breakpoints.
- Error/empty/loading copy, accessibility acceptance notes and frontend ADRs.
- A release bundle whose proxy, routes, app assets and deep links are tested.

## Workflow

1. Map the user journey from Mac desktop launch to Nexus upload, execution,
   review and export.
2. Define state and ownership before adding components; keep server state in a
   typed API/query layer and transient chrome state in the shell.
3. Implement the smallest reusable primitive in the owning app boundary.
4. Test normal, narrow, maximized, reload, reconnect, unauthorized and failure
   paths with keyboard and screen-reader semantics.
5. Run `npm run lint`, portfolio build, production build and browser checks.
6. Obtain frontend, desktop, backend, security and QA review before merge.
7. Record API/UI changes and screenshots in the project memory/release evidence.

## Tools

- React 19, TypeScript, Vite, CSS container queries and `lucide-react`.
- Playwright/browser devtools, axe-style accessibility checks and visual
  screenshots.
- Existing `reportApi.ts`, component tests and isolated legacy CRA builds.
- npm lockfiles, lint/type checking and CI artifact review.

## Restrictions

- Never put an LLM key, JWT secret or server credential in browser code,
  localStorage or bundled assets.
- Do not let an iframe reach into shell internals; use URL and postMessage
  contracts only when explicitly versioned and origin-checked.
- Do not replace the reference Mac for Web visual language without a product and
  architecture decision; avoid app-specific global CSS.
- Do not claim live progress from a timer or mock event; label unavailable data
  and reconnect to the durable API/SSE source.
- Do not make text/icons overflow a window or rely on a full-screen-only layout.

## Success Criteria

- Mac for Web launches a coherent desktop and any app can open, focus, resize,
  minimize, maximize and close without state leakage.
- Nexus clearly shows what the Agent is doing, what failed, what artifacts exist
  and when export is review-gated and ready.
- Store and Reddit remain isolated and responsive inside normal and maximized
  windows; adding a future app requires registry-level changes only.
- `npm run lint` and production build pass, with browser evidence for critical
  flows and no high-severity accessibility or security findings.
