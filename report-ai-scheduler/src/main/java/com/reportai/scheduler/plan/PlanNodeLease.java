package com.reportai.scheduler.plan;

import java.time.Instant;
import java.util.Objects;

/** Exclusive optimistic-lock lease held by exactly one worker. */
public record PlanNodeLease(String owner, String token, Instant issuedAt, Instant expiresAt) {
    public PlanNodeLease {
        owner = text(owner, "owner", 128);
        token = text(token, "token", 128);
        issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("lease expiry must be after issue time");
        }
    }

    public boolean activeAt(Instant at) {
        return !at.isBefore(issuedAt) && at.isBefore(expiresAt);
    }

    private static String text(String value, String field, int max) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isBlank() || normalized.length() > max
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }
}
