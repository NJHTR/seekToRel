package com.reportai.workflow;

public enum WorkflowStatus {
    CREATED,
    RUNNING,
    PAUSED,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }
}
