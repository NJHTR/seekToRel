package com.reportai.common.error;

import java.util.Map;
import java.util.Objects;

/** Safe, serializable error facts. Diagnostic exception text must stay in private logs. */
public record ErrorDetail(String code, FailureClass failureClass, boolean retryable,
                          String safeMessage, String recoveryHint, Map<String, String> metadata) {
    public ErrorDetail {
        code = code(code);
        failureClass = Objects.requireNonNull(failureClass, "failureClass");
        safeMessage = text(safeMessage, "safeMessage", 512);
        recoveryHint = recoveryHint == null ? "" : text(recoveryHint, "recoveryHint", 512);
        metadata = boundedMetadata(metadata);
    }

    private static String code(String value) {
        Objects.requireNonNull(value, "code");
        String normalized = value.strip();
        if (!normalized.matches("[A-Z][A-Z0-9_]{1,63}")) {
            throw new IllegalArgumentException("error code is invalid");
        }
        return normalized;
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

    private static Map<String, String> boundedMetadata(Map<String, String> source) {
        if (source == null || source.isEmpty()) return Map.of();
        if (source.size() > 16) throw new IllegalArgumentException("error metadata is too large");
        java.util.Map<String, String> copy = new java.util.TreeMap<>();
        source.forEach((key, value) -> {
            if (key == null || !key.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")
                    || value == null || value.length() > 256
                    || value.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("error metadata is invalid");
            }
            copy.put(key, value);
        });
        return Map.copyOf(copy);
    }
}
