# Mac for Web app manifest

`src/appManifest.ts` is the shell-level contract for applications hosted by
Mac for Web. It is intentionally small and local-first while the product is
still a single web bundle.

Each entry declares:

- stable `id` and user-facing `title`;
- `native` or `embedded` rendering kind;
- Dock visibility;
- default window geometry;
- capability names that future shell permissions can enforce.

Adding an app should not require editing window geometry, title maps or Dock
ordering in `DesktopShell.tsx`. The next platform step is to load signed,
versioned manifests from a registry and show health/version state before an
app receives a window.

Capability names are descriptive today. They are not a security boundary until
the shell's permission prompt and iframe/postMessage policy enforce them.
