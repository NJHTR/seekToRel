package com.reportai.workflow;

/** Commands accepted by the workflow state machine. */
public enum WorkflowAction {
    START,
    /** Persists the current node and opaque execution state without changing lifecycle status. */
    CHECKPOINT,
    PAUSE,
    RESUME,
    COMPLETE,
    FAIL,
    CANCEL
}
