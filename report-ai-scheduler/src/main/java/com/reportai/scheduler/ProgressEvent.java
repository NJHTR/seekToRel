package com.reportai.scheduler;

import java.time.Instant;
import java.util.Map;

public record ProgressEvent(String eventId, String taskId, int attempt, ProgressEventType type,
                            int percent, String message, Instant occurredAt, Map<String, String> attributes) {
    public ProgressEvent {
        if (eventId == null || eventId.isBlank() || taskId == null || taskId.isBlank() || attempt < 0
                || type == null || percent < 0 || percent > 100 || message == null || occurredAt == null) {
            throw new IllegalArgumentException("invalid progress event");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
