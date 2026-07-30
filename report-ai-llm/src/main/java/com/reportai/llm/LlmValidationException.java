package com.reportai.llm;

import java.util.List;

public final class LlmValidationException extends LlmException {
    private static final long serialVersionUID = 1L;
    private final List<String> violations;

    public LlmValidationException(List<String> violations) {
        super("LLM_REQUEST_INVALID", "LLM request failed safety validation");
        this.violations = violations == null ? List.of() : List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}
