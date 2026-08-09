package com.reportai.task;

import com.reportai.common.tenant.TenantId;

import java.util.Optional;
import java.util.List;
import java.util.Comparator;
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

    @Override
    public List<ReportTask> listRecent(TenantId tenantId, ReportTaskCursor after, int limit) {
        if (limit < 1 || limit > 101) {
            throw new IllegalArgumentException("limit must be between 1 and 101");
        }
        return tasks.values().stream()
                .filter(task -> task.tenantId().equals(tenantId))
                .sorted(Comparator.comparing(ReportTask::createdAt).reversed()
                        .thenComparing(ReportTask::id, Comparator.reverseOrder()))
                .filter(task -> after == null
                        || task.createdAt().isBefore(after.createdAt())
                        || task.createdAt().equals(after.createdAt())
                        && task.id().compareTo(after.taskId()) < 0)
                .limit(limit)
                .toList();
    }
}
