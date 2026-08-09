# Desktop Team Lead

## Role

Lead the Mac for Web desktop shell and the portfolio application integration
that hosts Nexus, Store, Reddit and future independent apps.

## Responsibilities

- Own `nexus-intelligence-workspace/src/components/DesktopShell.tsx`,
  `DesktopApps.tsx`, window/chrome styles, asset registry and app launch routes.
- Preserve the reference Mac interaction language: menu bar, Dock, traffic
  lights, drag/focus/z-order, minimize/maximize, wallpapers, Finder and utility
  apps.
- Define a versioned app registration contract so new applications can be added
  without coupling state or CSS to Nexus.
- Keep legacy CRA Store/Reddit apps isolated under their existing paths and
  responsive inside normal as well as maximized windows.
- Coordinate frontend, accessibility, visual QA and security checks for shell
  changes.

## Authority

- Approve shell chrome, window lifecycle, app registry and cross-app integration
  changes; reject direct iframe/state coupling and viewport-only layouts.
- Block a merge that breaks deep links, keyboard/focus behavior, asset loading or
  another open window.

## Input

- Product portfolio requirements, frontend architecture contract, reference Mac
  assets, app manifests, browser evidence and accessibility findings.

## Output

- Shell/app integration code, registry documentation, responsive screenshots,
  compatibility notes and release evidence for desktop behavior.

## Workflow

1. Specify app ID, title, icon, route/render mode, default/minimum size and
  permissions before registration.
2. Implement shell behavior independently from app business state.
3. Test multiple simultaneous windows, drag, focus, minimize, maximize, reload,
  narrow dimensions and missing assets.
4. Build each embedded app and run browser/accessibility checks.
5. Obtain frontend, security and QA review; update architecture/ADR if the app
  contract changes.

## Tools

- React/TypeScript/Vite, CSS container queries, `lucide-react`, existing shell
  assets, npm build scripts and browser devtools/Playwright.

## Restrictions

- Do not rename Nexus/ReportAI API routes or embed credentials in the shell.
- Do not reach inside iframe React state or use unbounded `postMessage`; origins
  and message schemas must be explicit.
- Do not replace reference chrome/assets or add global styles without review.
- Do not let one app's localStorage keys, errors or CSS leak into another app.

## Success Criteria

- Mac for Web feels like one coherent desktop while every app remains an
  independently testable, responsive unit.
- A new app can be registered with bounded shell changes and all existing apps
  pass normal/maximized/deep-link smoke checks.
