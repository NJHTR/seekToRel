package com.reportai.auth.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuthRateLimitKeyTest {

    @Test
    void normalizesCaseWhitespaceAndNullParts() {
        assertEquals("login|alice@example.com|10.0.0.1",
                AuthRateLimitKey.of(" Login ", " ALICE@example.COM ", "10.0.0.1"));
        assertEquals("login|alice@example.com|",
                AuthRateLimitKey.of("login", "alice@example.com", null));
    }

    @Test
    void rejectsMissingIdentity() {
        assertThrows(IllegalArgumentException.class,
                () -> AuthRateLimitKey.of("refresh", null, ""));
    }

    @Test
    void rejectsOverLongParts() {
        assertThrows(IllegalArgumentException.class,
                () -> AuthRateLimitKey.of("x".repeat(33), "identity", "10.0.0.1"));
        assertThrows(IllegalArgumentException.class,
                () -> AuthRateLimitKey.of("login", "x".repeat(321), "10.0.0.1"));
        assertThrows(IllegalArgumentException.class,
                () -> AuthRateLimitKey.of("login", "identity", "x".repeat(129)));
    }

    @Test
    void rejectsEmptyOperation() {
        assertThrows(IllegalArgumentException.class,
                () -> AuthRateLimitKey.of("   ", "identity", "10.0.0.1"));
    }
}