package com.reportai.scheduler;

public final class SchedulerIdempotencyConflictException extends RuntimeException {
    public SchedulerIdempotencyConflictException() {
        super("idempotency key was already used with a different task payload");
    }
}
