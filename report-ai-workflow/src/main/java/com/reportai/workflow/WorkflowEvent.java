package com.reportai.workflow;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Immutable audit event emitted after a workflow state transition. */
public record WorkflowEvent(String id, String workflowId, WorkflowAction action,
                            WorkflowStatus fromStatus, WorkflowStatus toStatus,
                            long version, Instant occurredAt, Map<String, Object> metadata) {
    public WorkflowEvent {
        if (id == null || id.isBlank() || workflowId == null || workflowId.isBlank()
                || action == null || fromStatus == null || toStatus == null || version < 0 || occurredAt == null) {
            throw new IllegalArgumentException("invalid workflow event");
        }
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
