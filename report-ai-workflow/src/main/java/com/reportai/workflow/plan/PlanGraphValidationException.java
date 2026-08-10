package com.reportai.workflow.plan;

import java.util.List;

/** Fail-closed validation error for a plan graph. */
public final class PlanGraphValidationException extends IllegalArgumentException {
    private final List<String> violations;

    public PlanGraphValidationException(List<String> violations) {
        super(String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> violations() {
        return violations;
    }
}
