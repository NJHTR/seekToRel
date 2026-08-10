package com.reportai.scheduler.plan;

import java.time.Instant;
import java.util.Objects;

/** Persisted reservation; its presence is the source of truth for resource accounting. */
public record ResourceReservation(ResourceRequest request, String owner, String leaseToken,
                                  Instant reservedAt) {
    public ResourceReservation {
        request = Objects.requireNonNull(request, "request");
        owner = text(owner, "owner", 128);
        leaseToken = text(leaseToken, "leaseToken", 128);
        reservedAt = Objects.requireNonNull(reservedAt, "reservedAt");
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
