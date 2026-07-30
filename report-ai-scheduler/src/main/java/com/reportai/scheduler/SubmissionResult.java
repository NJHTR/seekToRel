package com.reportai.scheduler;

public record SubmissionResult(ScheduledTask task, boolean created) {
    public SubmissionResult {
        if (task == null) {
            throw new IllegalArgumentException("task is required");
        }
    }
}
