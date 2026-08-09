package com.reportai.common.contract;

import java.util.Objects;

/** Shared validation for identifiers crossing a module boundary. */
final class IdentifierRules {
    private IdentifierRules() {
    }

    static String require(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (!normalized.equals(value) || !normalized.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }

    static String requireVersion(String value, String field) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (!normalized.equals(value) || normalized.isBlank() || normalized.length() > 128
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }
}
