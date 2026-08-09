package com.reportai.auth.port;

import com.reportai.auth.model.TenantPrincipal;

import java.util.Optional;

/**
 * Verifies a credential and returns a server-derived tenant principal.
 *
 * <p>An empty result means that no credential was supplied and the request may be anonymous. A
 * supplied but invalid/expired credential must fail with an authentication exception; silently
 * treating it as anonymous would create an authorization bypass.</p>
 */
@FunctionalInterface
public interface AuthenticationResolver {
    Optional<TenantPrincipal> resolve(AuthenticationRequest request);
}
