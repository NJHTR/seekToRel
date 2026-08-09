# Local Authentication

`local` uses ReportAI's first-party account provider. It creates a tenant and
its first user atomically during registration, then issues a 15-minute HS256
access token and a rotating opaque refresh token.

## Local development

The ignored `.env.auth.local` file supplies `REPORT_AI_AUTH_LOCAL_SIGNING_SECRET`.
It must contain at least 32 random bytes and must never be committed, copied to
browser configuration, or reused in another environment.

Start the backend using the `local` profile and open the UI at
`http://127.0.0.1:5173`. Register an account with a password of at least 12
characters containing uppercase, lowercase, numeric, and special characters.
The UI stores the session only in `sessionStorage` and rotates the refresh token
before an access token expires.

## HTTP endpoints

- `POST /api/v1/auth/register` creates a tenant and initial user.
- `POST /api/v1/auth/login` issues a fresh credential pair.
- `POST /api/v1/auth/refresh` rotates a refresh token.
- `POST /api/v1/auth/logout` revokes the supplied refresh token.

Refresh tokens are random 48-byte values; PostgreSQL stores only SHA-256 hashes.
Reuse of a revoked token revokes every outstanding refresh token for that user.
Login, registration, and refresh calls are bounded to ten attempts per minute per
identity/IP pair in this local deployment. Use a shared Redis-backed limiter for
multi-replica production deployments.

## Production requirements

Use a secret manager and an environment-specific signing key. Rotate it through
a staged key-id rollout, or replace the local issuer with the existing external
JWKS adapter. Enable TLS and put rate limiting at the gateway. Flyway V19 now
provides the persistence contract for pending-email accounts, one-time
verification/recovery digests, historical role assignments and security audit
events. Public exposure remains blocked until the application wires
`AccountSecurityTokenService` to an email delivery provider, atomically consumes
tokens, returns enumeration-safe recovery responses, and exposes audited
lock/unlock/role administration with distributed session revocation.
