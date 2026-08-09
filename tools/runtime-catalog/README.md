# Signed runtime catalog workflow

The catalog is administrator-owned configuration, not user input. It must be
generated only after the scientific image and its wheelhouse have been built.

Required catalog entries for the first Nexus profile:

- `python` → the pushed scientific image digest;
- `numpy==1.26.4`;
- `Pillow==10.4.0`;
- `matplotlib==3.9.2`;
- `torch==2.5.1`;
- `torchvision==0.20.1`;
- `PyYAML==6.0.2`;
- every transitive wheel selected by the lock file, each with its SHA-256.

The JSON is signed with detached Ed25519. The service verifies the signature,
schema, digest format, package coordinates and file limits before creating an
`EnvironmentResolver` bean. A missing or invalid signature is a startup error
when catalog mode is enabled.

After the scientific image has been pushed and its immutable digest recorded,
run `generate-signed-catalog.ps1` with that digest, the generated
`requirements.lock`, and the generated `catalog-dependencies.json`. The graph
is produced from pip's offline dependency report and records exact
`requires: [{name, version}]` edges. The script refuses lock files with unsafe
pip options, requires exactly one wheel hash per package, verifies that the
graph and lock file contain the same packages and hashes, then writes the
catalog, detached signature and PEM public key. Keep the private key outside
the repository.

Do not put credentials, registry tokens, private keys, or generated wheel files
in this repository. Supply them through the trusted build/release environment.
