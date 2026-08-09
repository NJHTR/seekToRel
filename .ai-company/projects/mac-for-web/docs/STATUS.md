# Status

State: baseline running; Phase 0 organization bootstrap in progress.

Verified: Nexus build, shell window behavior, responsive embedded Store/Reddit,
reference traffic lights, Notes icon and favicon. Open: formal app manifest and
health contract, accessibility audit, shell security review and release evidence.

The first app-manifest slice is now implemented in
`nexus-intelligence-workspace/src/appManifest.ts`: titles, Dock membership,
window geometry, rendering kind and declared capabilities are centralized. A
signed registry, health/version handshake and enforced permission prompts remain
future release work.
