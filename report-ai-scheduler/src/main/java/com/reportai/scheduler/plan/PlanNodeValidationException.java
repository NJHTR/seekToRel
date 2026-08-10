package com.reportai.scheduler.plan;

public final class PlanNodeValidationException extends PlanNodeSchedulerConflictException {
    public PlanNodeValidationException(String message) { super(message); }
}
