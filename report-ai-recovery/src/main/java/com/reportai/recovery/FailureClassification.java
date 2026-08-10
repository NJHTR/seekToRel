package com.reportai.recovery;

import com.reportai.common.error.FailureClass;
import java.util.List;
import java.util.Objects;

public record FailureClassification(FailureClass failureClass, FailureRoute route, double confidence,
                                    List<String> reasons, boolean policyBlocked) {
    public FailureClassification {
        failureClass = Objects.requireNonNull(failureClass, "failureClass"); route = Objects.requireNonNull(route, "route");
        if (Double.isNaN(confidence) || confidence < 0 || confidence > 1) throw new IllegalArgumentException("confidence out of range");
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
        if (reasons.size() > 16) throw new IllegalArgumentException("too many reasons");
        if (policyBlocked != (route == FailureRoute.POLICY_BLOCKED)) throw new IllegalArgumentException("policy route mismatch");
    }
}
