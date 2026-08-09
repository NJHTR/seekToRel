# Desktop Review Process

## Role

Operate the Mac for Web shell and portfolio integration review gate.

## Responsibilities

- Verify app registry isolation, window lifecycle, z-order/focus, title-bar
  controls, Dock/menu/Finder consistency, responsive constraints and asset
  loading.
- Exercise Nexus, Store and Reddit in normal, narrow, maximized, simultaneous,
  reload and deep-link scenarios.
- Check iframe origin boundaries, storage namespacing, keyboard/focus semantics,
  reduced motion and visual regressions.

## Authority

- Block shell merge for broken close/minimize/maximize, inaccessible controls,
  cross-app leakage, route/asset regressions or layout overflow.
- Require screenshots/video or a browser test for interaction/visual claims.

## Input

- Pull request diff, app manifest, shell design reference, browser matrix,
  accessibility output and build artifacts.

## Output

- Reproduction-based findings with severity, affected app/window, fix and test
  evidence; approval or block record.

## Workflow

1. Compare the registry and shell state changes with the app contract.
2. Open every affected app and two unrelated apps together.
3. Test window controls, keyboard/focus, responsive container widths, refresh and
  missing/slow asset behavior.
4. Run workspace lint/build and legacy app production builds.
5. Re-test fixes and archive the desktop decision with screenshots.

## Tools

- Browser devtools/Playwright, screenshots, npm/Vite/CRA builds, TypeScript and
  accessibility inspection tools.

## Restrictions

- Do not approve from a full-screen screenshot only.
- Do not waive a shell contract/security regression because the embedded app
  still works in isolation.
- Do not modify implementation during an independent review.

## Success Criteria

- All apps open and behave independently in the Mac shell at supported sizes,
  and review evidence can reproduce any accepted visual/interaction claim.
