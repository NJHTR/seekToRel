# report-ai-auth

Authentication and tenant-boundary primitives for ReportAI.

## Security contract

- `TenantPrincipal.tenantId` must be created only by a trusted `AuthenticationResolver` after
  verifying a JWT/session/API key or server-side identity record.
- No class in this module reads a client-supplied `tenantId` header, query parameter or body field.
- Application services call `AuthorizationPort` before loading or mutating tenant-owned data.
- `TenantContextFilter` establishes `AuthenticationContext` and clears both authentication and
  common tenant context in `finally`, preventing servlet thread reuse leaks.
- An absent credential is represented as `Optional.empty()`. A supplied invalid credential must be
  rejected by the resolver and must not be treated as anonymous.

## Integration

Provide a verifier in the application module:

```java
@Bean
AuthenticationResolver authenticationResolver(TokenVerifier verifier) {
    return request -> request.isAnonymous()
            ? Optional.empty()
            : Optional.of(verifier.verify(request.bearerToken(), request.path()));
}

@Bean
FilterRegistrationBean<TenantContextFilter> tenantContextFilter(AuthenticationResolver resolver) {
    FilterRegistrationBean<TenantContextFilter> registration = new FilterRegistrationBean<>(
            new TenantContextFilter(resolver));
    registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
    registration.addUrlPatterns("/api/*");
    return registration;
}
```

For public endpoints, the resolver returns empty only when no credential is supplied; controllers
or application services still enforce access with `AuthorizationPort.requireAuthenticated()` or
`requirePermission(...)`.

## Account lifecycle contract

`AccountSecurityTokenService` is the framework-independent boundary for email verification and
password recovery. It generates 256-bit URL-safe credentials, persists only lowercase SHA-256
digests, revokes older credentials for the same account/purpose before issuing a replacement, and
delegates one-time consumption to `AccountSecurityTokenRepository.consumeIfUsable(...)`. The
repository implementation must make that operation one atomic conditional update; a read followed
by an update is not safe under concurrent requests. Delivery adapters must not log the raw token,
and public request handlers must return the same response whether an email is registered or not.

`AccountEmailDelivery` is the delivery port. The optional application SMTP adapter receives
credentials only from environment-backed Spring mail properties. The original check-in script's
mailbox password and student credentials must not be reused or committed.

`AccountAdministrationPolicy` is the fail-closed authorization boundary for account lock/unlock,
disable/enable and role changes. Tenant administrators are restricted to their verified tenant;
platform administrators may operate across tenants; self-lockout and self-disable are rejected;
platform-role changes require platform administration. `AccountAdministrationRepository` requires
an expected account version for optimistic concurrency, and stores role history and immutable
`AccountAuditEntry` records instead of deleting administrative history.

Flyway migration `V19__account_lifecycle_security.sql` adds `PENDING_EMAIL`, versioned account
state, one-time token storage, historical role assignments and security audit events. The migration
is a persistence contract only: the application HTTP/service adapter and email delivery provider
must be wired before public registration is enabled.

## JWT adapter

`NimbusJwtCredentialVerifier` is a fail-closed adapter. Configure an exact issuer, audience and
non-empty signing-algorithm allow-list (the `rs256(...)` factory is a conservative default). It
requires `kid`, `sub`, `jti`, `exp` and the configured tenant claim (`tenant_id` by default), and
maps only the known `Permission` enum values. Invalid claims, signatures, algorithms, issuer,
audience or time windows all produce the same public-safe `invalid bearer credential` error.

Implement `JwtKeyResolver` with the deployment's JWKS client/cache. The resolver receives `(kid,
algorithm)` and may refresh the key set on an unknown key id; no key material or bearer token is
logged by this module. A typical wiring is:

```java
JwtVerificationPolicy policy = JwtVerificationPolicy.rs256("https://issuer.example", "report-ai");
JwtCredentialVerifier verifier = new NimbusJwtCredentialVerifier(jwksCache, policy);
AuthenticationResolver resolver = new JwtAuthenticationResolver(verifier);
```
