package com.reportai.llm.structured;

import java.util.List;

public final class StructuredResponseValidationException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final List<String> violations;

    public StructuredResponseValidationException(List<String> violations) {
        super("structured LLM response failed validation");
        this.violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}
