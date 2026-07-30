package com.reportai.auth.port;

import com.reportai.auth.model.TenantPrincipal;

import java.util.Objects;
import java.util.Optional;

/** AuthenticationResolver adapter for a JWT credential verifier. */
public final class JwtAuthenticationResolver implements AuthenticationResolver {
    private final JwtCredentialVerifier credentialVerifier;

    public JwtAuthenticationResolver(JwtCredentialVerifier credentialVerifier) {
        this.credentialVerifier = Objects.requireNonNull(credentialVerifier, "credentialVerifier");
    }

    @Override
    public Optional<TenantPrincipal> resolve(AuthenticationRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.isAnonymous()) {
            return Optional.empty();
        }
        // The verifier owns all failure handling. Invalid supplied credentials must not become
        // Optional.empty(), otherwise protected endpoints could be reached anonymously.
        return Optional.of(Objects.requireNonNull(
                credentialVerifier.verify(request.bearerToken()), "credentialVerifier result"));
    }
}
