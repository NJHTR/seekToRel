package com.reportai.auth.port;

import java.util.Objects;

/**
 * Sanitized request metadata passed to an authentication adapter.
 *
 * <p>There is intentionally no tenantId field. Tenant selection is a security decision made by
 * the verifier from trusted credential claims, not by a client-controlled header or query value.</p>
 */
public record AuthenticationRequest(
        String bearerToken,
        String method,
        String path,
        String remoteAddress) {

    public AuthenticationRequest {
        bearerToken = normalizeOptional(bearerToken);
        method = requireText(method, "method", 1, 16);
        path = requireText(path, "path", 1, 2048);
        remoteAddress = normalizeOptional(remoteAddress);
    }

    public boolean isAnonymous() {
        return bearerToken == null;
    }

    /** Do not include bearer credentials in logs or exception messages. */
    @Override
    public String toString() {
        return "AuthenticationRequest[method=" + method + ", path=" + path
                + ", remoteAddress=" + remoteAddress + ", bearerTokenPresent=" + (bearerToken != null) + "]";
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String requireText(String value, String name, int min, int max) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.length() < min || normalized.length() > max) {
            throw new IllegalArgumentException(name + " length must be between " + min + " and " + max);
        }
        return normalized;
    }
}
