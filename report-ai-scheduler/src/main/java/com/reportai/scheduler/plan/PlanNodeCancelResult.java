package com.reportai.scheduler.plan;

import java.util.Objects;

public record PlanNodeCancelResult(PlanNode node, boolean requested, boolean duplicate) {
    public PlanNodeCancelResult { node = Objects.requireNonNull(node, "node"); }
}
