package com.reportai.workflow.plan;

/** Role of a node in a persisted plan graph. */
public enum PlanNodeKind {
    START,
    TASK,
    END,
    JOIN,
    CONDITIONAL
}
