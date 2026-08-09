package com.reportai.scheduler;

import com.reportai.common.tenant.TenantId;

import java.util.Objects;

/** Adapts the scheduler's tenant-aware event callback to the durable history port. */
public final class PersistedProgressEventSink implements ProgressEventSink {
    private final ProgressEventHistoryRepository repository;

    public PersistedProgressEventSink(ProgressEventHistoryRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public void publish(ProgressEvent event) {
        throw new IllegalArgumentException("tenant-aware progress event publication is required");
    }

    @Override
    public void publish(TenantId tenantId, ProgressEvent event) {
        publishDurable(tenantId, event);
    }

    @Override
    public ProgressEvent publishDurable(TenantId tenantId, ProgressEvent event) {
        ProgressEventHistoryEntry entry = repository.append(Objects.requireNonNull(tenantId, "tenantId"),
                Objects.requireNonNull(event, "event"));
        return entry.event();
    }
}
