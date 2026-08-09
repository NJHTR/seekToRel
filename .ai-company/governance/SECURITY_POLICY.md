# Security Policy

## Default controls

- Authenticate and authorize every non-public operation; scope reads and writes
  by tenant and resource owner.
- Validate lengths, formats, MIME/magic bytes and archive expansion before
  parsing. Reject XXE, path traversal, command injection and unsafe redirects.
- Keep secrets in process injection or a secret manager. Never put API keys,
  passwords or tokens in source, logs, screenshots or test fixtures.
- Treat LLM output, uploaded documents and generated code as untrusted input.
  Validate tool names, schemas, budgets and permissions before execution.
- Use parameterized queries, safe output encoding, CSRF protection where cookie
  auth is used, rate limits and replay-resistant idempotency for writes.
- Emit security events without recording document text or credential material.

## Risk acceptance

Any exception names the asset, threat, compensating control, owner, expiry date
and rollback plan. Temporary local-development shortcuts must be profile-gated
and cannot silently become production defaults.
