package com.reportai.workflow.plan;

/** Dependency semantics between two plan nodes. */
public enum PlanEdgeSemantics {
    SERIAL,
    PARALLEL,
    CONDITIONAL,
    JOIN
}
