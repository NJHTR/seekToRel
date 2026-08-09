package com.reportai.auth.ratelimit;

import java.util.Locale;

/**
 * Canonical rate-limit bucket key builder.
 *
 * <p>Normalizes untrusted inputs (null, blank, excess length, case) and bounds
 * every part so a caller can never craft an unbounded or ambiguous key. Shared
 * by every {@link AuthAttemptGuard} implementation so buckets are portable
 * between in-memory and Redis backends.
 */
public final class AuthRateLimitKey {
    private static final int MAX_OPERATION_LENGTH = 32;
    private static final int MAX_IDENTITY_LENGTH = 320;
    private static final int MAX_ADDRESS_LENGTH = 128;

    private AuthRateLimitKey() {
    }

    public static String of(String operation, String identity, String clientAddress) {
        String safeOperation = normalize(operation, MAX_OPERATION_LENGTH);
        String safeIdentity = normalize(identity, MAX_IDENTITY_LENGTH);
        String safeAddress = clientAddress == null ? "" : clientAddress.trim();
        if (safeAddress.length() > MAX_ADDRESS_LENGTH) {
            throw new IllegalArgumentException("authentication attempt key is invalid");
        }
        if (safeOperation.isEmpty() || safeIdentity.isEmpty()) {
            throw new IllegalArgumentException("authentication attempt key is invalid");
        }
        return safeOperation + '|' + safeIdentity + '|' + safeAddress;
    }

    private static String normalize(String value, int maxLength) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException("authentication attempt key is invalid");
        }
        return normalized;
    }
}