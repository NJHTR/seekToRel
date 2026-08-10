package com.reportai.workflow.plan;

import java.util.Objects;

/** Small, dependency-free bounds shared by the persisted plan contracts. */
final class PlanRules {
    static final int MAX_NODES = 512;
    static final int MAX_EDGES = 2_048;
    static final int MAX_OUT_DEGREE = 64;
    static final int MAX_CONDITIONS = 128;
    static final int MAX_METADATA = 32;
    static final int MAX_TEXT = 128;

    private PlanRules() {
    }

    static String id(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (!normalized.equals(value)
                || !normalized.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }

    static String text(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isBlank() || normalized.length() > MAX_TEXT
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }

    static String condition(String value) {
        Objects.requireNonNull(value, "conditionRef");
        String normalized = value.strip();
        if (!normalized.equals(value) || normalized.isBlank() || normalized.length() > 64
                || !normalized.matches("[A-Za-z][A-Za-z0-9_.:-]{0,63}")) {
            throw new IllegalArgumentException("conditionRef is invalid");
        }
        return normalized;
    }
}
