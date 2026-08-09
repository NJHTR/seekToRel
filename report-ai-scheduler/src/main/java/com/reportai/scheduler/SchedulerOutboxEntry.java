package com.reportai.scheduler;

import com.reportai.common.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;

/**
 * A durable scheduler submission waiting to be published to {@link BoundedTaskScheduler}.
 * The outbox is intentionally scheduler-specific: the payload is immutable and the delivery
 * state is controlled by the storage adapter, not by an HTTP request thread.
 */
public record SchedulerOutboxEntry(
        String id,
        TenantId tenantId,
        QueuedTask task,
        OutboxStatus status,
        int attempts,
        Instant nextAttemptAt,
        String leaseOwner,
        Instant leaseUntil,
        String lastError,
        Instant createdAt,
        Instant updatedAt) {

    public SchedulerOutboxEntry {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("outbox id is required");
        }
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        task = Objects.requireNonNull(task, "task");
        status = Objects.requireNonNull(status, "status");
        if (attempts < 0) {
            throw new IllegalArgumentException("outbox attempts must not be negative");
        }
        nextAttemptAt = Objects.requireNonNull(nextAttemptAt, "nextAttemptAt");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (lastError != null && lastError.length() > 4096) {
            throw new IllegalArgumentException("outbox error is too long");
        }
    }

    public static SchedulerOutboxEntry pending(QueuedTask task) {
        Instant now = task.createdAt();
        return new SchedulerOutboxEntry(task.id(), task.tenantId(), task, OutboxStatus.PENDING,
                0, now, null, null, null, now, now);
    }

    public SchedulerOutboxEntry claimed(String owner, Instant until, Instant now) {
        return new SchedulerOutboxEntry(id, tenantId, task, OutboxStatus.IN_FLIGHT,
                attempts + 1, nextAttemptAt, owner, until, lastError, createdAt, now);
    }

    public enum OutboxStatus {
        PENDING,
        IN_FLIGHT,
        PUBLISHED
    }
}
