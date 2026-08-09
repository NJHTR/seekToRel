package com.reportai.common.contract;

import java.util.Objects;

public record EvaluationRisk(Severity severity, String code, String summary) {
    public EvaluationRisk {
        severity = Objects.requireNonNull(severity, "severity");
        code = text(code, "code", 128);
        summary = text(summary, "summary", 1024);
    }

    public enum Severity { LOW, MEDIUM, HIGH, CRITICAL }

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
