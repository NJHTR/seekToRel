# Nexus scientific runtime

This image is the approved CPU-only profile for the first Nexus GAN/CycleGAN
experiments. PyTorch and TorchVision are resolved from the trusted CPU wheel
index during image construction; it intentionally keeps package acquisition outside the runtime
service:

1. On a connected, trusted build host, resolve `requirements.in` including all
   transitive dependencies and hashes into `requirements.resolved.lock`.
2. Download one Linux wheel per resolved package into `wheelhouse/`; the build
   script then produces `requirements.lock` with exactly the SHA-256 of each
   selected wheel.
3. Run the offline pip dependency report and `generate_runtime_lock.py`; this
   produces both `requirements.lock` and `catalog-dependencies.json`.
4. Build this Dockerfile and run the import smoke test.
5. Push the image to the internal registry and record its immutable
   `repository@sha256:<64 hex characters>` digest.
6. Run `tools/runtime-catalog/generate-signed-catalog.ps1` with that image
   digest, `requirements.lock` and `catalog-dependencies.json`; only then set
   `REPORT_AI_RUNTIME_CATALOG_ENABLED=true`.

`requirements.lock` and `wheelhouse/` are deliberately not committed. A build
without them must fail instead of silently using PyPI or an unpinned package.

The runtime smoke test must use the same hardened filesystem contract as
production. Torch/TorchVision create short-lived temporary state during import,
so a bounded writable tmpfs is required even though the image root filesystem
is read-only:

```powershell
docker run --rm --network none --read-only `
  --tmpfs /tmp:rw,nosuid,nodev,noexec,size=64m,uid=65532,gid=65532 `
  --cap-drop ALL --security-opt no-new-privileges:true --user 65532:65532 `
  reportai/python-scientific@sha256:<image-digest> `
  python -c "import torch, torchvision, numpy, PIL, matplotlib, yaml; print('scientific imports ok')"
```

The runtime container is non-root, receives no network by default, and exposes
the packages only through `/opt/reportai/site-packages`.

## Local activation

After the digest-pinned image is loaded into Docker, enable the signed profile
in the backend environment (the paths are relative to the repository root):

```text
REPORT_AI_RUNTIME_ENABLED=true
REPORT_AI_RUNTIME_CATALOG_ENABLED=true
REPORT_AI_RUNTIME_CATALOG_PATH=runtime-images/scientific/local-catalog/scientific.catalog.json
REPORT_AI_RUNTIME_CATALOG_SIGNATURE_PATH=runtime-images/scientific/local-catalog/scientific.catalog.sig
REPORT_AI_RUNTIME_CATALOG_PUBLIC_KEY_PATH=runtime-images/scientific/local-catalog/catalog-ed25519-public.pem
REPORT_AI_RUNTIME_IMAGE_PULL_ENABLED=false
```

The service fails closed if the image is not already present or if the detached
catalog signature cannot be verified. Do not replace these paths with an
unsigned catalog or a mutable image tag.
