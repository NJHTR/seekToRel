package com.reportai.task;

import com.reportai.common.tenant.TenantId;

import java.util.Optional;

public interface ReportTaskRepository {
    ReportTask save(ReportTask task);

    /** Updates only when the caller still owns the expected optimistic-lock version. */
    boolean update(ReportTask task, long expectedVersion);

    Optional<ReportTask> findById(TenantId tenantId, String taskId);
}
