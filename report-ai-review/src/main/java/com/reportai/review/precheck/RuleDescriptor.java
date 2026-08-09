package com.reportai.review.precheck;

import java.util.Objects;

public record RuleDescriptor(String id, PrecheckStage stage, PrecheckSeverity severity,
                             String description, int scoreWeight) {
    public RuleDescriptor {
        id = text(id, "id", 64);
        stage = Objects.requireNonNull(stage, "stage");
        severity = Objects.requireNonNull(severity, "severity");
        description = text(description, "description", 256);
        if (scoreWeight < 0 || scoreWeight > 100) throw new IllegalArgumentException("scoreWeight out of range");
    }
    private static String text(String value, String field, int max) {
        Objects.requireNonNull(value, field);
        var normalized = value.strip();
        if (normalized.isBlank() || normalized.length() > max || normalized.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException(field + " is invalid");
        return normalized;
    }
}
