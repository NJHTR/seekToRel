package com.reportai.workflow.plan;

import com.reportai.common.contract.CommandId;
import com.reportai.common.contract.EventId;

import java.time.Instant;

/** Deterministic event construction for the convenience repository API. */
final class PlanRunEventFactory {
    private PlanRunEventFactory() {
    }

    static PlanRunEvent forTransition(PlanRun expected, PlanRun next, String idempotencyKey) {
        String suffix = next.runId() + "-v" + next.version();
        return new PlanRunEvent(new EventId("plan-event-" + suffix),
                new CommandId("plan-command-" + suffix), idempotencyKey,
                next.runId(), next.tenantId(), next.planId(), next.planVersion(),
                expected.state(), next.state(), null, next.nodeStateDigest(), next.version(),
                next.updatedAt() == null ? Instant.EPOCH : next.updatedAt());
    }
}
