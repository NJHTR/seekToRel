package com.reportai.scheduler;

import java.util.Objects;

/** Event plus its database-assigned ordering key. The sequence is never client supplied. */
public record ProgressEventHistoryEntry(long eventSequence, ProgressEvent event) {
    public ProgressEventHistoryEntry {
        if (eventSequence < 1) {
            throw new IllegalArgumentException("eventSequence must be positive");
        }
        event = Objects.requireNonNull(event, "event");
    }
}
