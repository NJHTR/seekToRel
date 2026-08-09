package com.reportai.scheduler;

/** Exclusive keyset cursor backed by the database-assigned monotonic event sequence. */
public record ProgressEventCursor(long eventSequence) {
    public ProgressEventCursor {
        if (eventSequence < 1) {
            throw new IllegalArgumentException("eventSequence must be positive");
        }
    }
}
