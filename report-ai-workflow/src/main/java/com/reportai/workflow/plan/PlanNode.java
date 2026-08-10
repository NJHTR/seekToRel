package com.reportai.workflow.plan;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Immutable node declaration. Node IDs are the only graph references. */
public record PlanNode(String nodeId, PlanNodeKind kind, String operation, Map<String, String> metadata) {
    /** Source-compatible alias for callers that model the kind as a nested type. */
    public enum Type {
        START,
        TASK,
        END,
        JOIN,
        CONDITIONAL
    }

    public PlanNode {
        nodeId = PlanRules.id(nodeId, "nodeId");
        kind = Objects.requireNonNull(kind, "kind");
        operation = operation == null ? nodeId : PlanRules.text(operation, "operation");
        metadata = boundedMetadata(metadata);
    }

    public PlanNode(String nodeId, String operation) {
        this(nodeId, PlanNodeKind.TASK, operation, Map.of());
    }

    public PlanNode(String nodeId, PlanNodeKind kind) {
        this(nodeId, kind, nodeId, Map.of());
    }

    public PlanNode(String nodeId, Type kind, String operation, Map<String, String> metadata) {
        this(nodeId, PlanNodeKind.valueOf(Objects.requireNonNull(kind, "kind").name()), operation, metadata);
    }

    public PlanNode(String nodeId, Type kind) {
        this(nodeId, PlanNodeKind.valueOf(Objects.requireNonNull(kind, "kind").name()));
    }

    public static PlanNode start(String nodeId) {
        return new PlanNode(nodeId, PlanNodeKind.START, "start", Map.of());
    }

    public static PlanNode task(String nodeId, String operation) {
        return new PlanNode(nodeId, PlanNodeKind.TASK, operation, Map.of());
    }

    public static PlanNode task(String nodeId) {
        return task(nodeId, nodeId);
    }

    public static PlanNode conditional(String nodeId, String operation) {
        return new PlanNode(nodeId, PlanNodeKind.CONDITIONAL, operation, Map.of());
    }

    public static PlanNode join(String nodeId) {
        return new PlanNode(nodeId, PlanNodeKind.JOIN, "join", Map.of());
    }

    public static PlanNode end(String nodeId) {
        return new PlanNode(nodeId, PlanNodeKind.END, "end", Map.of());
    }

    private static Map<String, String> boundedMetadata(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        if (source.size() > PlanRules.MAX_METADATA) {
            throw new IllegalArgumentException("node metadata is too large");
        }
        TreeMap<String, String> copy = new TreeMap<>();
        source.forEach((key, value) -> {
            if (key == null || !key.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")
                    || value == null || value.length() > 512
                    || value.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("node metadata is invalid");
            }
            copy.put(key, value);
        });
        return Collections.unmodifiableMap(copy);
    }
}
