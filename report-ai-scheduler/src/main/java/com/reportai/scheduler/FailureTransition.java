package com.reportai.scheduler;

public record FailureTransition(SchedulerTaskStatus status, boolean retryScheduled, long version) {
    public FailureTransition {
        if (status == null || version < 0) {
            throw new IllegalArgumentException("invalid failure transition");
        }
    }
}
