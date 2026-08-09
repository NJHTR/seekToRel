# Task-scoped dependency environment lifecycle

## Decision

ReportAI treats generated dependencies as untrusted build input. A task never
executes `pip install` from generated source and an execution container never
gets general Internet access. The first implementation supports exact Python
coordinates from an image-provided, read-only wheelhouse:

```text
PREFLIGHT -> PREPARE_DEPENDENCIES -> RUN -> COLLECT -> CLEANUP
```

The Python tool uses pip's isolated, no-index mode. The current two-container
path stages wheels in `/tmp/reportai-deps`, then copies them into a task-scoped
Docker volume mounted at `/home/reportai` and exposed to Python as
`/home/reportai/.reportai-deps`. Mounting the volume at the image-owned parent
directory keeps it writable by UID 65532 without a root init container and
avoids pip's cross-device `--target` rename failure. Legacy single-container
requests use an ephemeral `/tmp/reportai-deps` target. Both paths remove
temporary requirements and dependency state. `ExecutionRequest` remains
source-compatible with the legacy constructor; the dependency list is optional
and empty by default.

## Production target

The production lifecycle uses two containers:

1. An installer container with a pinned installer image, a fixed internal
   package proxy or wheelhouse, and a bounded egress policy. It writes a venv or
   immutable environment layer identified by `imageDigest + lockDigest`.
2. An execution container with `network=none`, a read-only environment mount,
   a task-specific workspace, a non-root UID, dropped capabilities, a read-only
   root filesystem and bounded CPU, memory, PIDs, disk and output.

Both containers are labeled with tenant and task identifiers. The scheduler
records a checkpoint after preflight and after environment preparation. Every
success, failure, timeout, cancellation and process restart invokes the cleanup
Saga; a reconciler removes abandoned containers and task environments.

## Manifest contract

The next API-compatible extension is an immutable `EnvironmentManifest` stored
beside the agent checkpoint. It contains:

- language, relative entry point and schema version;
- exact package name/version pairs and, when using a wheelhouse, SHA-256 hashes;
- required input files/directories with size and optional content hashes;
- selected runtime image digest and capability profile;
- a canonical SHA-256 manifest digest.

Planner and Coder may request package names and data IDs, but may not choose an
image, package URL, proxy, credential or pip flag. A resolver fills those values
from an administrator-approved catalog and locks the manifest before execution.
Retries must reuse the same manifest digest.

The resolved digest is also copied to `report_task.manifest_digest` at the
first successful Preflight boundary. The database validates its SHA-256 shape
and a trigger rejects every later replacement, so separate Planner/Coder,
Runtime, Review or export checkpoints cannot silently switch environments.

## Security gates

The resolver rejects unpinned packages, direct URLs, VCS/local paths, index
overrides, unknown packages and dependency-confusion names. Installer and
execution images are digest-pinned. Credentials are never placed in generated
environment variables. Package SBOM, vulnerability and license checks are
release gates for shared caches. Caches are content-addressed, read-only to
tasks and garbage-collected by TTL; a task can never write the shared cache.

Missing input data stops in preflight with an actionable error. The system never
downloads an arbitrary dataset or invents an execution result.

## Milestones

- M1 (implemented): exact dependency coordinates, offline wheelhouse command,
  temporary target cleanup, network denial, allow-list and unit coverage.
- M2a (implemented): immutable manifest model, canonical digest, input/hash
  preflight validator, safe legacy adapter, and optional handler preflight for
  explicitly resolved manifests. This slice deliberately does not resolve
  package names or invent wheel hashes.
- M2b (implemented): immutable administrator-owned catalog resolver now resolves
  exact legacy Python coordinates to digest-pinned runtime profiles and wheel
  SHA-256 hashes without network access. Durable `Preflight` checkpoints,
  wiring resolver output into the task handler, two-container
  installation/execution and cleanup reconciliation are wired. The runtime API
  translates a resolved manifest into a hash-locked pip request; no catalog is
  enabled by default until its signed configuration is supplied.
- M2c (implemented): explicit manifests now execute through a durable
  `coder -> preflight -> runtime` cursor, with legacy `coder -> runtime`
  checkpoints migrated without rerunning completed code generation. The
  resolver output is forwarded as hash-locked dependency metadata. Signed
  Signed catalog loading is now wired at startup when enabled. The local
  installer/executor split uses a task-scoped ephemeral Docker volume and
  deterministic cleanup; disk quota and trusted scientific image evidence
  remain release gates.
- M2d (implemented): the first resolved manifest digest is persisted on the
  tenant-scoped report task and made immutable at the database boundary;
  retries may reuse it but cannot replace it.
- M3: signed scientific image profile with NumPy/Pillow/Torch/TorchVision,
  content-addressed environment cache and cancellation/resume drills.
- M4: approved proxy egress, SBOM/CVE/license gates, uv lock support and other
  languages/GPU profiles.

The default local image is intentionally `python:3.12-slim`; it does not claim
to contain scientific packages. A scientific task is runnable only after an
approved scientific image or wheelhouse is installed and its digest/capability
profile is configured.
## Runtime hardening updates

When signed catalog mode is enabled, ReportTaskHandler treats any document or
planner environment description as intent only. `EnvironmentResolver` selects
the image and wheel hashes, and `RuntimeConfiguration.withCatalog` installs
those image digests into the Docker allow-list before execution.

Installer and Execution share a task-scoped Docker volume mounted at
`/home/reportai` and use only `/home/reportai/.reportai-deps` for installed
packages; source and generated artifacts remain in the workspace bind mount.
The volume is removed after either success or failure, and startup
reconciliation removes labeled volumes which have no attached container. A
filesystem reconciler separately removes stale dependency staging files left
by a process crash.

OpenAI-compatible chat calls request SSE streaming and accept both SSE deltas
and ordinary JSON responses. The agent contract remains synchronous at the
domain boundary, while the transport keeps long model responses active.
