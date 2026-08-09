# Mac for Web / Nexus

This React 19 application is Mac for Web, a macOS-style portfolio desktop.
Nexus is one application inside the desktop rather than the desktop itself. The current
application set includes:

- Finder-style portfolio and project browser
- Nexus experiment-report workspace
- Commerce Store portfolio application
- Reddit Client portfolio application
- locally persisted Notes
- functional Calculator
- interactive Calendar
- wallpaper selection and multi-window controls

The Nexus application keeps the Forge, Flow, Editor and Inspector interaction
model while using ReportAI's real backend:

- registration, login, refresh and logout
- DOCX upload and canonical document parsing
- task creation with idempotency
- live task progress over authenticated SSE
- phase/Agent activity and failure details
- retry and review-gated DOCX download

## Run locally

The ReportAI Spring Boot backend should be running on `http://127.0.0.1:18080`.
PostgreSQL and MinIO remain behind the existing SSH tunnels used by the
backend. Then run:

```powershell
npm install
npm run build:portfolio-apps
npm run dev
```

Open `http://localhost:3000` for Mac for Web. The isolated Nexus
application route is `/workspace`; the desktop hosts it in its own window. The
local server proxies only `/api/v1/*` to
`REPORT_AI_API_TARGET` (default `http://127.0.0.1:18080`). Set that variable
when the backend uses another address.

The desktop server hosts the two legacy CRA applications at their existing
base paths (`/react-ecommerce-store/` and `/typescript-reddit-clone/`). This
keeps their React 18 dependency trees isolated from the React 19 desktop.

New portfolio applications start in the manifest at `src/appManifest.ts`.
It owns the application id, title, kind, Dock visibility, default window size,
and declared capabilities. Rendering remains in `src/components/DesktopApps.tsx`,
while the shell consumes the manifest for window state and Dock order. This
keeps application metadata from becoming another hard-coded shell contract.

For a production bundle:

```powershell
npm run lint
npm run build
npm start
```

The server-side proxy is intentionally narrow and does not accept arbitrary
upstream URLs. API credentials belong in the Spring Boot process environment,
not in this frontend project.
