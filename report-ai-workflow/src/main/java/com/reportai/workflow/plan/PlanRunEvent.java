package com.reportai.workflow.plan;

import com.reportai.common.contract.CommandId;
import com.reportai.common.contract.EventId;
import com.reportai.common.contract.Sha256Digest;
import com.reportai.common.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;

/** Append-only transition event for a plan run. */
public record PlanRunEvent(EventId eventId, CommandId commandId, String idempotencyKey,
                           String runId, TenantId tenantId, String planId, long planVersion,
                           PlanRunState from, PlanRunState to, String nodeId,
                           String nodeStateDigest, long version, Instant occurredAt) {
    public PlanRunEvent {
        eventId = Objects.requireNonNull(eventId, "eventId");
        commandId = Objects.requireNonNull(commandId, "commandId");
        idempotencyKey = PlanRules.text(idempotencyKey, "idempotencyKey");
        runId = PlanRules.id(runId, "runId");
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        planId = PlanRules.id(planId, "planId");
        if (planVersion <= 0) throw new IllegalArgumentException("planVersion must be positive");
        from = Objects.requireNonNull(from, "from");
        to = Objects.requireNonNull(to, "to");
        if (nodeId != null) nodeId = PlanRules.id(nodeId, "nodeId");
        nodeStateDigest = new Sha256Digest(nodeStateDigest).value();
        if (version <= 0) throw new IllegalArgumentException("event version must be positive");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
    }

    public boolean sameCommand(PlanRunEvent other) {
        return other != null && commandId.equals(other.commandId)
                && idempotencyKey.equals(other.idempotencyKey) && runId.equals(other.runId)
                && tenantId.equals(other.tenantId) && planId.equals(other.planId)
                && planVersion == other.planVersion && from == other.from && to == other.to
                && Objects.equals(nodeId, other.nodeId)
                && nodeStateDigest.equals(other.nodeStateDigest) && version == other.version;
    }
}
