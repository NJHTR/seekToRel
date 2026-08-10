package com.reportai.scheduler.plan;

public final class PlanNodeIdempotencyConflictException extends PlanNodeSchedulerConflictException {
    public PlanNodeIdempotencyConflictException(String message) { super(message); }
}
