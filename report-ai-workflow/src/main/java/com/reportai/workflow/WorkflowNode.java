package com.reportai.workflow;

/** Declarative node; execution is delegated to an agent/tool outside this module. */
public record WorkflowNode(String id, String name, WorkflowNodeType type) {
    public WorkflowNode {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("workflow node id must not be blank");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("workflow node name must not be blank");
        }
        if (type == null) {
            throw new IllegalArgumentException("workflow node type must not be null");
        }
    }
}
