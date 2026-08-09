package com.reportai.api.report;

import java.time.Instant;
import java.util.Map;

/** Public, redacted progress event representation; no task payload or tenant internals. */
public record ProgressEventResponse(long eventSequence, String eventId, String taskId, int attempt, String type,
                                    int percent, String message, Instant occurredAt,
                                    Map<String, String> attributes) {
    public ProgressEventResponse {
        if (eventSequence < 1) {
            throw new IllegalArgumentException("eventSequence must be positive");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
