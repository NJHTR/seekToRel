package com.reportai.task;

import com.reportai.common.tenant.TenantId;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Test/development adapter only. Production must use a tenant-scoped DB repository with optimistic locking. */
public final class InMemoryReportTaskRepository implements ReportTaskRepository {
    private final ConcurrentMap<String, ReportTask> tasks = new ConcurrentHashMap<>();

    @Override
    public ReportTask save(ReportTask task) {
        tasks.put(task.tenantId().value() + ":" + task.id(), task);
        return task;
    }

    @Override
    public boolean update(ReportTask task, long expectedVersion) {
        String key = task.tenantId().value() + ":" + task.id();
        AtomicBoolean updated = new AtomicBoolean(false);
        tasks.computeIfPresent(key, (ignored, current) -> {
            if (current.version() != expectedVersion) {
                return current;
            }
            updated.set(true);
            return task;
        });
        return updated.get();
    }

    @Override
    public Optional<ReportTask> findById(TenantId tenantId, String taskId) {
        return Optional.ofNullable(tasks.get(tenantId.value() + ":" + taskId));
    }
}
