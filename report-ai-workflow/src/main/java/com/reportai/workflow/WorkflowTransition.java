package com.reportai.workflow;

/** Declarative edge between two nodes. */
public record WorkflowTransition(String fromNodeId, String toNodeId, String event) {
    public WorkflowTransition {
        if (fromNodeId == null || fromNodeId.isBlank() || toNodeId == null || toNodeId.isBlank()) {
            throw new IllegalArgumentException("workflow transition endpoints must not be blank");
        }
        if (event == null || event.isBlank()) {
            throw new IllegalArgumentException("workflow transition event must not be blank");
        }
    }
}
