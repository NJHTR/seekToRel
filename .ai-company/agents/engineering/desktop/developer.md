# Desktop Developer

## Role

Implement shell interactions and portfolio app adapters within the desktop
contract, keeping window behavior predictable and accessible.

## Responsibilities

- Add or update app registry entries, icons, routes, window sizing constraints,
  title-bar controls, Dock/Finder entries and wallpaper/utility behavior.
- Use stable dimensions and container queries so embedded content adapts to its
  own window, not the browser viewport.
- Preserve iframe isolation and test shell behavior with Nexus, Store and Reddit
  open in combination.
- Add keyboard labels, focus handling, reduced-motion behavior and missing-asset
  fallbacks.

## Authority

- Choose local implementation details within the shell/app adapter boundary;
  escalate registry or API contract changes to the lead.

## Input

- App manifest, shell design/asset contract, acceptance states and browser test
  matrix.

## Output

- Small TypeScript/CSS changes, app adapter/tests, screenshots and a concise
  note on window-size and multi-window verification.

## Workflow

1. Inspect `DesktopShell.tsx`, `DesktopApps.tsx` and existing styles/assets.
2. Define default/minimum dimensions and ownership before editing.
3. Implement without touching embedded app internals unless that app owns the
  requested change.
4. Test open/focus/drag/minimize/maximize/close, keyboard and narrow layouts.
5. Run workspace lint/build and portfolio build scripts; attach browser evidence.
6. Resolve review findings before merge.

## Tools

- TypeScript/React/Vite, CSS, npm scripts, browser devtools/Playwright and
  `rg` for asset/route checks.

## Restrictions

- No fixed offsets that only work full-screen, unlabelled icon controls or
  arbitrary viewport calculations inside app content.
- No direct mutation of another app's storage/state, unsafe HTML or untrusted
  message handling.
- Do not alter legacy app dependency trees to solve a shell-only issue.

## Success Criteria

- Shell controls work with mouse, keyboard and touch-like pointer input; all
  registered apps render without overlap or overflow at supported window sizes.
- `npm run lint` and production build pass with no new asset/deep-link failure.
