package com.reportai.workflow;

import java.util.Map;
import java.util.Set;

/** Versioned, immutable workflow definition suitable for persistence as JSON. */
public record WorkflowDefinition(
        String id,
        int version,
        String startNodeId,
        Map<String, WorkflowNode> nodes,
        Set<WorkflowTransition> transitions) {

    public WorkflowDefinition {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("workflow definition id must not be blank");
        }
        if (version < 1) {
            throw new IllegalArgumentException("workflow definition version must be positive");
        }
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("workflow definition must contain nodes");
        }
        if (!nodes.containsKey(startNodeId)) {
            throw new IllegalArgumentException("start node does not exist");
        }
        nodes = Map.copyOf(nodes);
        transitions = transitions == null ? Set.of() : Set.copyOf(transitions);
        for (WorkflowTransition transition : transitions) {
            if (!nodes.containsKey(transition.fromNodeId()) || !nodes.containsKey(transition.toNodeId())) {
                throw new IllegalArgumentException("workflow transition references an unknown node");
            }
        }
    }
}
