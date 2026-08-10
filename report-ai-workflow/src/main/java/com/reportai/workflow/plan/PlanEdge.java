package com.reportai.workflow.plan;

import java.util.Objects;

/** Immutable dependency edge. Conditional edges carry an allow-listed condition key;
 * join edges carry the target join node reference. No expression language is accepted. */
public record PlanEdge(String fromNodeId, String toNodeId, PlanEdgeSemantics semantics, String reference) {
    /** Source-compatible alias for callers that model edge semantics as a nested type. */
    public enum Semantics {
        SERIAL,
        PARALLEL,
        CONDITIONAL,
        JOIN
    }

    public PlanEdge {
        fromNodeId = PlanRules.id(fromNodeId, "fromNodeId");
        toNodeId = PlanRules.id(toNodeId, "toNodeId");
        semantics = Objects.requireNonNull(semantics, "semantics");
        reference = normalizeReference(semantics, reference, toNodeId);
    }

    public PlanEdge(String fromNodeId, String toNodeId, PlanEdgeSemantics semantics) {
        this(fromNodeId, toNodeId, semantics, null);
    }

    public PlanEdge(String fromNodeId, String toNodeId, Semantics semantics) {
        this(fromNodeId, toNodeId,
                PlanEdgeSemantics.valueOf(Objects.requireNonNull(semantics, "semantics").name()));
    }

    public PlanEdge(String fromNodeId, String toNodeId, Semantics semantics, String reference) {
        this(fromNodeId, toNodeId,
                PlanEdgeSemantics.valueOf(Objects.requireNonNull(semantics, "semantics").name()), reference);
    }

    public static PlanEdge serial(String fromNodeId, String toNodeId) {
        return new PlanEdge(fromNodeId, toNodeId, PlanEdgeSemantics.SERIAL);
    }

    public static PlanEdge parallel(String fromNodeId, String toNodeId) {
        return new PlanEdge(fromNodeId, toNodeId, PlanEdgeSemantics.PARALLEL);
    }

    public static PlanEdge conditional(String fromNodeId, String toNodeId, String conditionRef) {
        return new PlanEdge(fromNodeId, toNodeId, PlanEdgeSemantics.CONDITIONAL, conditionRef);
    }

    public static PlanEdge join(String fromNodeId, String toNodeId) {
        return new PlanEdge(fromNodeId, toNodeId, PlanEdgeSemantics.JOIN, toNodeId);
    }

    public static PlanEdge join(String fromNodeId, String toNodeId, String joinRef) {
        return new PlanEdge(fromNodeId, toNodeId, PlanEdgeSemantics.JOIN, joinRef);
    }

    public String conditionRef() {
        return semantics == PlanEdgeSemantics.CONDITIONAL ? reference : null;
    }

    public String joinRef() {
        return semantics == PlanEdgeSemantics.JOIN ? reference : null;
    }

    private static String normalizeReference(PlanEdgeSemantics semantics, String reference, String toNodeId) {
        return switch (semantics) {
            case CONDITIONAL -> PlanRules.condition(reference);
            case JOIN -> PlanRules.id(reference == null ? toNodeId : reference, "joinRef");
            case SERIAL, PARALLEL -> {
                if (reference != null) {
                    throw new IllegalArgumentException("serial and parallel edges cannot carry a reference");
                }
                yield null;
            }
        };
    }
}
