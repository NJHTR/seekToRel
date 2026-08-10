package com.reportai.scheduler.plan;

import java.util.Objects;

public record PlanNodeSubmitResult(PlanNode node, boolean created) {
    public PlanNodeSubmitResult { node = Objects.requireNonNull(node, "node"); }
}
