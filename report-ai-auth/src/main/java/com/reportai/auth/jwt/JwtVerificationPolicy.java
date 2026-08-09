package com.reportai.auth.jwt;

import com.nimbusds.jose.JWSAlgorithm;

import java.time.Duration;
import java.util.Set;

/** Immutable, fail-closed JWT verification policy. */
public record JwtVerificationPolicy(
        String issuer,
        String audience,
        Set<JWSAlgorithm> allowedAlgorithms,
        Duration clockSkew,
        JwtClaimNames claimNames) {

    public JwtVerificationPolicy {
        issuer = requireText(issuer, "issuer", 512);
        audience = requireText(audience, "audience", 256);
        if (allowedAlgorithms == null || allowedAlgorithms.isEmpty()) {
            throw new IllegalArgumentException("allowedAlgorithms must not be empty");
        }
        allowedAlgorithms = Set.copyOf(allowedAlgorithms);
        if (allowedAlgorithms.stream().anyMatch(algorithm -> algorithm == null
                || JWSAlgorithm.NONE.equals(algorithm))) {
            throw new IllegalArgumentException("none algorithm is not allowed");
        }
        if (clockSkew == null || clockSkew.isNegative() || clockSkew.compareTo(Duration.ofMinutes(5)) > 0) {
            throw new IllegalArgumentException("clockSkew must be between 0 and 5 minutes");
        }
        claimNames = claimNames == null ? JwtClaimNames.defaults() : claimNames;
    }

    public static JwtVerificationPolicy rs256(String issuer, String audience) {
        return new JwtVerificationPolicy(issuer, audience, Set.of(JWSAlgorithm.RS256),
                Duration.ofSeconds(30), JwtClaimNames.defaults());
    }

    private static String requireText(String value, String name, int maxLength) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(name + " is invalid");
        }
        return value.trim();
    }
}
