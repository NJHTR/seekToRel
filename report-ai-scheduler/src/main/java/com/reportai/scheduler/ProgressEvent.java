package com.reportai.scheduler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

public record ProgressEvent(String eventId, String taskId, int attempt, ProgressEventType type,
                            int percent, String message, Instant occurredAt, Map<String, String> attributes,
                            long eventSequence) {
    private static final String IDENTIFIER = "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}";
    private static final int MAX_MESSAGE_LENGTH = 4096;
    private static final int MAX_ATTRIBUTE_COUNT = 32;
    private static final int MAX_ATTRIBUTE_VALUE_LENGTH = 512;
    private static final Set<String> SAFE_ATTRIBUTE_KEYS = Set.of(
            "phase", "stage", "language", "tool", "artifactType", "artifactId", "executionId",
            "errorCode", "retryable", "reasonCode", "node", "operation");

    public ProgressEvent {
        if (!validIdentifier(eventId) || !validIdentifier(taskId) || attempt < 0
                || type == null || percent < 0 || percent > 100 || message == null
                || occurredAt == null || eventSequence < 0) {
            throw new IllegalArgumentException("invalid progress event");
        }
        message = sanitizeMessage(message);
        attributes = sanitizeAttributes(attributes);
    }

    /** Creates a new, not-yet-persisted event. The database assigns its sequence on append. */
    public ProgressEvent(String eventId, String taskId, int attempt, ProgressEventType type,
                         int percent, String message, Instant occurredAt,
                         Map<String, String> attributes) {
        this(eventId, taskId, attempt, type, percent, message, occurredAt, attributes, 0L);
    }

    /** Removes multiline/control content and turns likely secret/exception text into a generic update. */
    public static String sanitizeMessage(String source) {
        String normalized = source.replaceAll("[\\p{Cc}&&[^\\r\\n\\t]]", "")
                .replaceAll("[\\r\\n\\t]+", " ").trim();
        if (normalized.length() > MAX_MESSAGE_LENGTH) {
            normalized = normalized.substring(0, MAX_MESSAGE_LENGTH);
        }
        if (normalized.matches("(?i).*"
                + "(password|passwd|secret|api[-_ ]?key|authorization|bearer|private[ ]+key|"
                + "prompt|payload|stdout|stderr|exception|stack[ ]+trace|sk-[a-z0-9]).*")) {
            return "progress update";
        }
        return normalized;
    }

    /**
     * Only low-risk, bounded metadata is allowed to cross the event boundary. In particular,
     * exception messages, prompts, payloads and credentials must not become an SSE or audit
     * history data channel. Callers that need diagnostics should use a stable errorCode.
     */
    public static Map<String, String> sanitizeAttributes(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        Map<String, String> safe = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (safe.size() >= MAX_ATTRIBUTE_COUNT) {
                return;
            }
            String normalized = safeAttributeValue(key, value);
            if (key != null && value != null && SAFE_ATTRIBUTE_KEYS.contains(key)
                    && normalized != null) {
                safe.put(key, normalized);
            }
        });
        return Map.copyOf(safe);
    }

    private static String safeAttributeValue(String key, String value) {
        if (value == null || value.isBlank() || value.length() > MAX_ATTRIBUTE_VALUE_LENGTH) {
            return null;
        }
        if ("retryable".equals(key)) {
            return "true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)
                    ? value.toLowerCase(java.util.Locale.ROOT) : null;
        }
        return value.matches("[A-Za-z0-9][A-Za-z0-9._:/-]{0,127}") ? value : null;
    }

    private static boolean validIdentifier(String value) {
        return value != null && value.matches(IDENTIFIER);
    }
}
