package com.reportai.workflow.plan;

/** Durable lifecycle of a plan run. */
public enum PlanRunState {
    PENDING,
    RUNNING,
    WAITING,
    COMPLETED,
    FAILED,
    CANCELLED,
    PAUSED
}
