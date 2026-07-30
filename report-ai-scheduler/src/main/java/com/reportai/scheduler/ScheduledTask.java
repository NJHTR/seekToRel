package com.reportai.scheduler;

import com.reportai.common.tenant.TenantId;

import java.time.Instant;

/** Snapshot returned by a persistence adapter. Version is an optimistic-lock token. */
public record ScheduledTask(String id, TenantId tenantId, String type, String payload,
                            String idempotencyKey, RetryPolicy retryPolicy, int attempt,
                            SchedulerTaskStatus status, Instant nextRunAt, String leaseOwner,
                            Instant leaseUntil, long version, Instant createdAt, Instant updatedAt) {
    public ScheduledTask {
        if (id == null || id.isBlank() || tenantId == null || type == null || type.isBlank()
                || payload == null || idempotencyKey == null || retryPolicy == null || attempt < 0
                || status == null || nextRunAt == null || version < 0 || createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("invalid scheduled task");
        }
    }
}
