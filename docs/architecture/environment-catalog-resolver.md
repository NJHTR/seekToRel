# Administrator environment catalog (M2b)

`EnvironmentCatalog` is an immutable, administrator-owned allow-list. It is
deliberately a pure in-process domain component: it never resolves DNS, calls a
package index, runs `pip`, or downloads an artifact.

`EnvironmentResolver` accepts the legacy `PythonDependencyRequest` and resolves
each exact `name==version` coordinate against the catalog. A catalog entry must
already contain a 64-character wheel SHA-256. The selected runtime profile must
contain a digest-pinned image and a capability profile. The result is an
`EnvironmentManifest` with a deterministic manifest digest:

```text
untrusted request → catalog lookup → locked dependency hashes/image
                  → immutable EnvironmentManifest → preflight → sandbox
```

Unknown languages and packages fail with structured
`EnvironmentCatalogException.Code` values. Unpinned requests are rejected;
the resolver never guesses a version and never accepts a URL, VCS coordinate,
index override, or pip flag. Input requirements are passed through to the
manifest and are checked later by `EnvironmentManifestValidator`.

The catalog can be loaded from an administrator-controlled signed file at
startup; when it is not enabled, tests and local development may construct it
in code. No unsigned catalog fallback is used when the feature is enabled.

## Signed file loading

The application can load the catalog at startup when
`REPORT_AI_RUNTIME_CATALOG_ENABLED=true`. The payload, detached signature and
Ed25519 public key are supplied through `REPORT_AI_RUNTIME_CATALOG_PATH`,
`REPORT_AI_RUNTIME_CATALOG_SIGNATURE_PATH` and
`REPORT_AI_RUNTIME_CATALOG_PUBLIC_KEY` (or `..._PUBLIC_KEY_PATH`). Payload and
signature files must be regular non-symlink files within the configured size
limit. Signature verification occurs before JSON parsing; a failure aborts
startup rather than silently falling back to an unsigned catalog.

The payload schema is deliberately small:

```json
{
  "schemaVersion": 1,
  "profiles": [{
    "language": "python",
    "imageDigest": "python@sha256:<64 hex chars>",
    "capabilityProfile": "scientific"
  }],
  "dependencies": [{
    "name": "numpy",
    "version": "1.26.4",
    "sha256": "<64 hex chars>"
  }]
}
```

Catalog payloads can now be authenticated with Ed25519 using
`SignedCatalogVerifier`; signature verification is deliberately separate from
JSON parsing and catalog construction. The application must verify the signed
bytes before deserializing profiles or dependencies.
