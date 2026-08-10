package com.reportai.scheduler.plan;

import com.reportai.common.contract.TaskId;
import com.reportai.common.tenant.TenantId;

import java.util.Objects;

/** Stable, tenant-scoped identity of a node in a versioned execution plan. */
public record PlanNodeRef(TenantId tenantId, TaskId taskId, String planId,
                          long planVersion, String nodeId) {
    public PlanNodeRef {
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        taskId = Objects.requireNonNull(taskId, "taskId");
        planId = text(planId, "planId", 128);
        if (planVersion < 1) {
            throw new IllegalArgumentException("planVersion must be positive");
        }
        nodeId = text(nodeId, "nodeId", 128);
    }

    public PlanNodeRef(String tenantId, String taskId, String planId, long planVersion, String nodeId) {
        this(new TenantId(tenantId), new TaskId(taskId), planId, planVersion, nodeId);
    }

    public String durableKey() {
        return tenantId.value() + '\u0000' + taskId.value() + '\u0000' + planId
                + '\u0000' + planVersion + '\u0000' + nodeId;
    }

    private static String text(String value, String field, int max) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isBlank() || normalized.length() > max
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }
}
