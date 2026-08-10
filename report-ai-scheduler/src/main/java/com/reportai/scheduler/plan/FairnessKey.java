package com.reportai.scheduler.plan;

import java.time.Instant;
import java.util.Objects;

/**
 * Durable ordering fields used to prevent one tenant from monopolising a priority band.
 * Values are supplied by the command boundary and therefore survive process restarts.
 */
public record FairnessKey(long tenantReadySequence, long globalReadySequence, Instant enqueuedAt) {
    public FairnessKey {
        if (tenantReadySequence < 0 || globalReadySequence < 0) {
            throw new IllegalArgumentException("fairness sequences must not be negative");
        }
        enqueuedAt = Objects.requireNonNull(enqueuedAt, "enqueuedAt");
    }

    public FairnessKey(long tenantReadySequence, long globalReadySequence) {
        this(tenantReadySequence, globalReadySequence, Instant.EPOCH);
    }

    public static FairnessKey first(long globalReadySequence, Instant enqueuedAt) {
        return new FairnessKey(0, globalReadySequence, enqueuedAt);
    }
}
