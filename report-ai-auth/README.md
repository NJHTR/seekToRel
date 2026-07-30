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
