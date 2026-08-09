package com.reportai.auth.port;

import com.reportai.auth.model.TenantPrincipal;

/**
 * Verifies a bearer JWT and maps only verified claims to an application principal.
 *
 * <p>Implementations must fail closed: a supplied token that is malformed, unsigned, expired,
 * issued by the wrong authority, or missing a required identity claim must throw an
 * authentication exception. It must never be converted to an anonymous request.</p>
 */
@FunctionalInterface
public interface JwtCredentialVerifier {

    TenantPrincipal verify(String bearerToken);
}
