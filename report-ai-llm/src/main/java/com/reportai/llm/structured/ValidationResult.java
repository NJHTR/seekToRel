package com.reportai.llm.structured;

import java.util.List;

public record ValidationResult(boolean valid, List<String> violations) {
    public ValidationResult {
        violations = violations == null ? List.of() : List.copyOf(violations);
        if (valid && !violations.isEmpty()) {
            throw new IllegalArgumentException("valid result cannot contain violations");
        }
    }

    public static ValidationResult success() {
        return new ValidationResult(true, List.of());
    }

    public static ValidationResult invalid(String violation) {
        return new ValidationResult(false, List.of(violation == null ? "invalid response" : violation));
    }
}
