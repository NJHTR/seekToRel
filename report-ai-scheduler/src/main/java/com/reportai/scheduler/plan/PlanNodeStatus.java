package com.reportai.scheduler.plan;

/** Durable node lifecycle. Cancellation is intentionally a request followed by an acknowledgement. */
public enum PlanNodeStatus {
    READY,
    CLAIMED,
    RUNNING,
    CANCELLATION_REQUESTED,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
