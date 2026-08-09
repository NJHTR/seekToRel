package com.reportai.runtime;

import java.util.List;

/** Deterministic result of validating a manifest against one workspace. */
public record PreflightResult(boolean accepted, List<Violation> violations) {
    public PreflightResult {
        violations = violations == null ? List.of() : List.copyOf(violations);
        if (accepted && !violations.isEmpty()) {
            throw new IllegalArgumentException("accepted preflight cannot contain violations");
        }
    }

    public static PreflightResult success() { return new PreflightResult(true, List.of()); }

    public List<Violation> errors() { return violations; }

    public record Violation(String code, String path, String message) {
        public Violation {
            if (code == null || code.isBlank() || message == null || message.isBlank()) {
                throw new IllegalArgumentException("preflight violation requires code and message");
            }
            path = path == null ? "" : path;
        }
    }
}
