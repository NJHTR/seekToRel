package com.reportai.workflow.plan;

/** Durable lifecycle of an individual node execution. */
public enum PlanNodeState {
    PENDING,
    READY,
    RUNNING,
    WAITING,
    SUCCEEDED,
    FAILED,
    BLOCKED,
    CANCELLED,
    SKIPPED
}
