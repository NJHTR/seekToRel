package com.reportai.workflow;

import java.util.Map;

/** A durable workflow aggregate snapshot. State is intentionally opaque to the engine. */
public record WorkflowInstance(
        String id,
        String definitionId,
        int definitionVersion,
        String taskId,
        String currentNodeId,
        WorkflowStatus status,
        Map<String, Object> state,
        long version) {

    /** Backward-compatible constructor for callers that only have the original fields. */
    public WorkflowInstance(String id, String definitionId, WorkflowStatus status,
                            Map<String, Object> state, long version) {
        this(id, definitionId, 1, null, null, status, state, version);
    }

    public WorkflowInstance {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("workflow id must not be blank");
        }
        if (definitionId == null || definitionId.isBlank()) {
            throw new IllegalArgumentException("definition id must not be blank");
        }
        if (definitionVersion < 1) {
            throw new IllegalArgumentException("definition version must be positive");
        }
        if (status == null) {
            throw new IllegalArgumentException("workflow status must not be null");
        }
        if (version < 0) {
            throw new IllegalArgumentException("workflow version must not be negative");
        }
        state = state == null ? Map.of() : Map.copyOf(state);
    }

    public WorkflowInstance withTransition(WorkflowStatus targetStatus, long nextVersion) {
        return new WorkflowInstance(id, definitionId, definitionVersion, taskId,
                currentNodeId, targetStatus, state, nextVersion);
    }

    /**
     * Returns a new durable snapshot with the latest execution node and state.
     * Lifecycle status is intentionally unchanged; callers must use the workflow
     * state machine for pause/resume/terminal transitions.
     */
    public WorkflowInstance withCheckpoint(String nodeId, Map<String, Object> nextState, long nextVersion) {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("workflow node id must not be blank");
        }
        return new WorkflowInstance(id, definitionId, definitionVersion, taskId,
                nodeId, status, nextState, nextVersion);
    }
}
