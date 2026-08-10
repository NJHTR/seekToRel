package com.reportai.workflow.plan;

/** Optimistic concurrency, identity, and replay conflict. */
public final class PlanConflictException extends RuntimeException {
    public PlanConflictException(String message) {
        super(message);
    }
}
