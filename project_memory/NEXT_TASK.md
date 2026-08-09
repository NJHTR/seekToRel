# Next Task

## 2026-08-08 immutable task manifest boundary

- Added `report_task.manifest_digest` with a SHA-256 constraint and a PostgreSQL
  trigger that rejects replacement after the first Preflight lock.
- `ReportTaskHandler` now persists the resolved manifest digest before Review;
  retries can reuse it but cannot switch the runtime environment. Runtime-enabled
  tasks fail closed when no resolved digest exists.
- Added unit coverage for the domain lock and a PostgreSQL integration check for
  durability and mutation rejection.
- Added a real Docker lifecycle integration check that invokes Installer,
  Execution and Cleanup as separate calls over the task-scoped dependency volume.
- Evidence: Java 21 application/task/storage tests pass; the Docker-backed
  integration suite passes 15 infrastructure tests plus Redis rate-limit tests.

## 2026-08-08 catalog/runtime hardening update

- Signed Catalog mode now locks Python dependency coordinates and SHA-256 values at
  the runtime boundary. Direct `RuntimeEngine` callers cannot submit an approved
  package name with an unapproved version/hash or omit the resolved Catalog entry.
- `DockerRuntimeEngine` no longer reports Installer bookkeeping files
  (`.reportai-deps` and `.reportai-requirements.txt`) as user artifacts before
  cleanup removes them.
- Catalog pre-resolution is deferred when no runtime intent exists yet, so Coder's
  import-derived dependencies become the first immutable manifest instead of
  causing a false `ENV_MANIFEST_CHANGED` failure.
- Evidence: Java 21 full Maven test suite passes; runtime catalog-lock regression
  tests and the production UI build pass.

Remaining P0 gates are unchanged: persist Installer/Execution/Cleanup as distinct
recoverable lifecycle stages, split read-only inputs from writable outputs, run a
real dataset-backed DOCX end-to-end task, and archive Docker/security/recovery
evidence before release.

Priority order for the next autonomous iterations:

1. Restart the IDEA backend so the latest catalog and import-to-dependency bridge are loaded, then rerun the supplied DOCX with the runtime-only provider key. The deterministic bridge now turns generated Python imports into catalog-resolved exact coordinates before Preflight.
2. Run authenticated Nexus browser acceptance for Archive, history pagination, refresh, SSE `Last-Event-ID` reconnect, failure recovery and review-gated export.
3. Repeat the green local integration suite in CI and archive PostgreSQL, MinIO, Redis and runtime evidence.
4. Add email verification, password recovery and audited user administration before public sign-up; consider making the distributed Redis rate limiter the default backend behind a gateway.
   The environment-backed `AccountEmailDelivery` port and optional SMTP adapter now exist; PostgreSQL token persistence and HTTP lifecycle wiring remain required.
5. Validate the OWASP dependency scan on a warmed cache (CI requires `NVD_API_KEY` for predictable runtime).
6. Verify digest-pinned sandbox images, non-root/capability restrictions and image pull behavior in container CI.
7. Verify scheduler restart recovery, duplicate delivery and cross-tenant authorization under concurrency on real PostgreSQL.
8. Execute and record the backup/restore, scheduler-recovery and staging observability drills using `docs/operations/backup-restore.md`.
9. Implement and test managed egress policy/proxy enforcement and split read-only input from read-write output before allowing runtime network capabilities.

Current release blockers are tracked in `docs/production-release-checklist.md` and `.ai/TODO.md`.

Latest runtime progress (2026-08-08): `PythonImportDependencyResolver` recognizes the
approved scientific module set (`torch`, `torchvision`, `numpy`, `PIL`,
`matplotlib`, `yaml`, and related packages) without allowing the model to choose
versions or hashes. `EnvironmentResolver` maps an unpinned package-name intent to
the single matching version in the signed catalog and still rejects missing or
ambiguous entries. `ReportTaskHandler` derives this intent after Coder and resolves
the manifest before the durable Preflight step. Runtime tests: 34 (1 expected skip);
application reactor tests: 33 passed.

The active YAML defaults now match the bounded Coder policy: `coder-timeout=PT105S`
and `read-timeout=PT180S`. This keeps each provider attempt below the observed
two-minute gateway limit while allowing a lower-effort fallback and preserving
environment-variable overrides for trusted gateways with a different budget.

The execution bridge also re-resolves dependencies after Coder. A pre-Coder
zero-dependency manifest is treated as provisional, so generated `torch`/
`torchvision` imports cannot be masked by the earlier preview manifest; an
explicit complete user manifest still remains immutable and is checked for
catalog equality.

Frontend follow-up:

1. Run the Nexus frontend browser flow with an authenticated LLM provider and capture the
   upload, live execution, failure and export states.
2. Verify the durable Archive and Observatory reducer in a browser after refresh and
   a simulated network flap; the backend endpoint and client reconnect contract are now implemented.

Completed in the current frontend cycle: window-level container queries and
narrow-window overflow verification for the active macOS portfolio shell. The
remaining frontend work is the authenticated end-to-end task flow and durable
history described above.

Completed in the current visual pass: reference traffic-light assets, shared
Notes icon and active favicon wiring, with browser resource verification.

Completed in the current embedded-app pass: Reddit's fixed full-screen feed
offset was replaced with a window-safe grid and verified at the normal and
maximized Nexus window sizes. The next frontend release gate remains the
authenticated LLM-backed upload-to-export flow.

Completed in the current auth resilience pass: rotated refresh tokens are now
protected by a frontend single-flight guard, stale sessions are cleared on 401,
and rate-limited refresh attempts cool down before retry. The next browser gate
is to sign in again once, let the session expire or force a refresh, and verify
that Archive/SSE requests share one refresh call and recover without a loop.

The latest task diagnosis is now explicit: Planner returned HTTP 200, while
Coder was rejected by the local LLM request validator before network I/O with
`LLM_REQUEST_INVALID`. Restart the backend to load bounded violation logging,
then create a fresh task; its log will identify the exact policy field and the
task will stop immediately instead of retrying three times.

The exact policy defect is fixed: `max_completion_tokens` was falsely rejected
because the validator matched the substring `token`. Restart the IDEA backend
to load the corrected validator, then create a new task; the Coder request will
be sent to the provider instead of being stopped locally.

Coder now defaults to 2,048 completion tokens and low reasoning effort, with a
90-second local attempt. The hard safety ceiling remains 16,384 and the values
are configurable through `REPORT_AI_LLM_CODER_MAX_OUTPUT_TOKENS`,
`REPORT_AI_LLM_CODER_REASONING_EFFORT` and `REPORT_AI_LLM_CODER_TIMEOUT`.

The failed 45% task was traced to the former provider read timeout,
not parsing or Docker. The timeout and request budget are now 180 seconds and failures surface the
stable `LLM_TIMEOUT` code. A backend restart and a newly-created task are needed
to exercise the fix; terminal tasks are intentionally not reset in place.

Branding is now separated for future portfolio growth: `Mac for Web` is the
desktop shell and `Nexus` is the ReportAI application inside it.

## Company bootstrap follow-up

The `.ai-company` operating system and its first cross-functional review are
complete. `NEXUS-101` and `NEXUS-102` implementation is present and reviewed;
the remaining gate is reproducible Docker/browser/release evidence before
production approval.

## Latest live diagnosis

The current provider request is valid. A real Coder request completed with HTTP
200 in about 75 seconds, but the local response guard rejected the gateway's
aggregate `completion_tokens` because reasoning tokens are counted differently
by this provider. That guard and the Runtime checkpoint state-loss defect are
fixed and covered by tests. Restart the IDEA backend from the rebuilt classes,
then run one fresh DOCX task to verify `EXECUTING -> REVIEWING -> EXPORTING`.

The provider now accepts streaming chat responses. The remaining latency gate
is operational verification against the real gateway: confirm its SSE heartbeat
cadence, configure an explicit idle deadline and total task deadline, and run a
fresh Coder task after the IDEA backend is restarted. The immediate configurable
workaround remains configurable through `REPORT_AI_LLM_CODER_TIMEOUT`; the
active YAML default is now `PT105S` with provider read-timeout `PT180S`, below
the observed gateway origin limit.

## Sandbox environment track (2026-08-07)

M2a is implemented: immutable environment manifests, canonical digests,
input/hash preflight checks, and explicit handler preflight for resolved
manifests. M2b now includes an offline administrator-owned `EnvironmentCatalog`
and `EnvironmentResolver`; approved coordinates produce image- and wheel-hash-
locked manifests. Resolved dependencies use pip `--require-hashes` and clean
their temporary requirements/target files on container exit.

The next release gate is durable `Coder -> Preflight -> Runtime` checkpointing
plus catalog loading from signed administrator configuration. The local
`python:3.12-slim` image still does not contain scientific packages, so Torch
experiments remain blocked until a scientific image or approved wheelhouse is
provisioned.

## 2026-08-07 continuation evidence

- `ReportTaskHandler` now resolves runtime intent through the signed
  `EnvironmentResolver` whenever catalog mode is enabled. Client/planner image
  digests and dependency hashes are no longer authoritative; the catalog result
  is persisted with the task manifest digest before Coder runs.
- `RuntimeConfiguration.withCatalog(...)` feeds catalog-approved image digests
  into the Docker runtime while retaining platform-owned command builders.
- Manifest digests now hash canonical JSON with fixed field order and sorted
  dependency/input arrays.
- Runtime image pulling is disabled by default. A missing digest-pinned image
  fails as “not preloaded” unless the explicit development override
  `REPORT_AI_RUNTIME_IMAGE_PULL_ENABLED=true` is set.
- OpenAI-compatible chat requests now ask for `stream=true` and accept both
  normal JSON and SSE responses. SSE content deltas are assembled into the
  existing `ChatResponse` contract, so Planner/Coder can keep a gateway
  connection active without changing the agent API.
- A bounded `WorkspaceCleanupReconciler` runs at runtime startup and on an
  interval, removing only stale `.reportai-deps` and
  `.reportai-requirements.txt` files left by crashed installer/executor
  containers; source and artifact files are never swept.
- Real Docker execution now uses a labeled task-scoped ephemeral volume for
  dependency packages shared by Installer and Execution. The volume is removed
  in the execution `finally` path, and Docker startup reconciles labeled
  volumes with no attached containers. The host workspace remains the source /
  artifact boundary.
- Added `runtime-images/scientific` and `tools/runtime-catalog` build
  instructions. They deliberately require a trusted connected build host to
  generate `requirements.lock`, wheelhouse hashes, the scientific image digest,
  and the detached Ed25519 catalog signature; no fabricated hashes are stored.
- Evidence: Java 21 `report-ai-runtime` tests pass (26 tests, 1 skipped),
  selected application catalog/preflight tests pass (5 tests), and the
  application reactor compiles successfully. The Docker integration test
  `InfrastructureContainerTest#dockerRuntimeExecutesOnlyTheBoundedWorkspaceProgram`
  also passes against the local daemon, proving the non-root, no-capability,
  no-new-privileges execution boundary with image pull disabled by default.

Remaining release gate: perform the trusted scientific image/wheelhouse build,
publish its digest, generate/sign the catalog, then run the supplied GAN/CycleGAN
DOCX through the complete installer → execution → cleanup → review → export
flow. The catalog and image cannot be marked production-ready until that
external supply-chain evidence exists.

M2c progress: report execution now uses a durable `coder -> preflight ->
runtime` pipeline. Legacy `coder -> runtime` checkpoints migrate in place,
preserving the cursor and manifest digest. Resolved dependency hashes are
forwarded to the Python tool and installed with `--require-hashes`. An Ed25519
catalog signature verifier is covered by runtime tests; signed catalog file
loading and the two-container installer/executor remain the next release gate.

## 2026-08-08 dependency-closure and local scientific-build evidence

- Added `DependencyCoordinate` and `CatalogDependency`; catalog entries now carry
  exact `requires` edges.
- `EnvironmentCatalog` validates missing nodes, duplicate edges and cycles, and
  `EnvironmentResolver` materializes the complete transitive closure into the
  immutable manifest instead of only the user's direct packages.
- Signed catalog loading accepts the optional `requires` graph while retaining
  compatibility with earlier flat catalog payloads.
- The trusted build generator now derives `catalog-dependencies.json` from pip's
  offline dependency report and refuses graph/lock package or hash mismatches.
- The first scientific profile is explicitly CPU-only (`torch==2.5.1+cpu`,
  `torchvision==0.20.1+cpu`) to avoid implicit CUDA supply-chain expansion on the
  local Windows host. Runtime installation remains fully offline from the
  wheelhouse.
- Local evidence completed on 2026-08-08: 23 hash-locked Linux wheels,
  dependency graph with 23 nodes, signed Ed25519 catalog, digest-pinned local
  image `reportai/python-scientific@sha256:6aafbe96a24e380848caebd5c370abb62cafc845bb03b656bcb770d7407ac425`,
  image smoke import of Torch/TorchVision/NumPy/Matplotlib/PyYAML, and OpenSSL
  signature verification. The private signing key remains outside the repo.
- Java evidence: `report-ai-runtime` 31 tests (1 expected skip), LLM 16 tests,
  and the full application reactor suite (33 application tests) pass,
  including transitive closure, missing-node and cycle cases.
- Frontend evidence: `report-ai-ui` `npm run build` passes (`vue-tsc` plus Vite
  production build), and the current local Vite server responds with HTTP 200
  on port 3000.

Remaining gate: configure the generated catalog in the restarted IDEA backend,
then run the supplied GAN/CycleGAN DOCX through the real Coder → Installer →
Runtime → Review → DOCX export path. The local image/catalog are not yet a
published registry release; publishing must happen on the trusted release host.

## 2026-08-08 installer/execution verification

- A real Docker integration test exposed and fixed the non-root dependency
  staging defect. Mounting a named volume directly at a not-yet-existing
  subdirectory caused Docker to create a root-owned mountpoint; pip also failed
  when `--target` crossed from `/tmp` to that volume.
- Installer now stages hash-locked packages in `/tmp/reportai-deps`, copies them
  into `/home/reportai/.reportai-deps`, and shares a task-scoped volume mounted
  at the image-owned `/home/reportai` parent. Execution mounts that dependency
  volume read-only; cleanup removes the volume and temporary requirements.
- Evidence after the fix: Runtime tests 35 (1 expected skip), application tests
  33 passed, signed checked-in scientific catalog load test passed, Docker
  infrastructure test 9 passed including actual NumPy install/execution and
  cleanup, and `report-ai-ui` production build passed.
- Remaining release gate is unchanged: inject a runtime-only LLM key and real
  experiment dataset, then run a fresh authenticated DOCX task through
  `Coder -> Preflight -> Installer -> Execution -> Cleanup -> Reviewer -> Export`.

The supplied target DOCX currently contains only the report template and one
2,603-byte embedded `word/media/image1.png`; it contains no GAN/CycleGAN data
directories. The scientific preflight therefore now fails closed when
Torch/TorchVision is requested without declared real inputs, with stable code
`PYTHON_INPUT_MISSING`, instead of allowing synthetic fixture results.

## 2026-08-08 hardened scientific runtime verification

- Added a real Docker integration regression test for the digest-pinned scientific
  image. It imports Torch and TorchVision, performs a tensor operation, and writes
  an artifact while running as UID 65532 with all capabilities dropped, no network,
  a read-only root filesystem, and the bounded `/tmp` tmpfs supplied by
  `DockerJavaSandboxClient`.
- The explicit integration run passed: `InfrastructureContainerTest` 10 tests,
  0 failures, 0 errors, 0 skipped. This closes the earlier ambiguity where a raw
  `docker run --read-only` smoke command omitted `/tmp` and made TorchVision fail
  during import; production and test now exercise the same contract.
- The scientific image README now documents the hardened smoke command and why a
  writable, size-limited `/tmp` is required without weakening the read-only rootfs.

The next unverified gate remains the authenticated application task: configure
the signed catalog and runtime-only LLM key in the restarted IDEA backend, supply
real GAN/CycleGAN inputs, and capture the complete Coder -> Preflight -> Installer
-> Execution -> Cleanup -> Reviewer -> DOCX export evidence.

## 2026-08-08 live task diagnosis and guard

- Browser acceptance reached the authenticated Nexus Forge, uploaded and parsed
  `广州理工学院实验报告模板.docx`, and showed durable Planner heartbeats and
  retry events.
- The failed task's persisted checkpoint proves the IDEA process was stale: its
  pipeline remained `agent_types=["coder","runtime"]` and the Runtime executed
  a Coder-generated `status=prerequisite_failure` program. The resulting exit
  code was incorrectly classified as generic `PYTHON_RUNTIME_ERROR`.
- The current source now detects the explicit Coder prerequisite-failure contract,
  returns `PYTHON_INPUT_MISSING` from Coder, and prevents that source from entering
  Runtime. `CoderAgentFailureTest` passes with the new regression case.
- The scheduler now preserves known domain error prefixes from non-retryable
  failures in the durable terminal event. Missing scientific inputs will surface
  as `PYTHON_INPUT_MISSING` in the UI instead of only `TASK_NOT_RETRYABLE`.
  `BoundedTaskSchedulerTest` covers this event contract.
- The Nexus timeline now exposes the real 12-node lifecycle (Catalog, Preflight,
  Installer and Cleanup are explicit) and maps durable event text to the active
  node. The production UI build passes after this change.
- Restart the IDEA backend from the freshly compiled Java 21 classes before any
  new task. A new checkpoint must show `agent_types=["coder","preflight","runtime"]`;
  only then can the live task verify the new deterministic guard and signed Catalog.
