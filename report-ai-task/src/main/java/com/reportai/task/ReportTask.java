package com.reportai.task;

import com.reportai.common.exception.BusinessException;
import com.reportai.common.api.ErrorCode;
import com.reportai.common.tenant.TenantId;

import java.time.Instant;

public record ReportTask(String id, TenantId tenantId, String documentObjectKey,
                         ReportTaskStatus status, long version, Instant createdAt, Instant updatedAt) {
    public ReportTask {
        if (id == null || id.isBlank() || tenantId == null || documentObjectKey == null || documentObjectKey.isBlank()
                || status == null || version < 0 || createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("invalid report task");
        }
    }

    public ReportTask transitionTo(ReportTaskStatus target) {
        if (!isAllowed(status, target)) {
            throw new BusinessException(ErrorCode.CONFLICT, "task cannot transition from " + status + " to " + target);
        }
        Instant now = Instant.now();
        return new ReportTask(id, tenantId, documentObjectKey, target, version + 1, createdAt, now);
    }

    private static boolean isAllowed(ReportTaskStatus current, ReportTaskStatus target) {
        return switch (current) {
            case CREATED -> target == ReportTaskStatus.PARSING || target == ReportTaskStatus.CANCELLED;
            case PARSING -> target == ReportTaskStatus.PLANNING || target == ReportTaskStatus.FAILED
                    || target == ReportTaskStatus.PAUSED;
            case PLANNING -> target == ReportTaskStatus.EXECUTING || target == ReportTaskStatus.FAILED
                    || target == ReportTaskStatus.PAUSED;
            case EXECUTING -> target == ReportTaskStatus.REVIEWING || target == ReportTaskStatus.FAILED
                    || target == ReportTaskStatus.PAUSED;
            case REVIEWING -> target == ReportTaskStatus.EXPORTING || target == ReportTaskStatus.FAILED
                    || target == ReportTaskStatus.PAUSED;
            case EXPORTING -> target == ReportTaskStatus.SUCCEEDED || target == ReportTaskStatus.FAILED;
            case PAUSED -> target == ReportTaskStatus.PARSING || target == ReportTaskStatus.PLANNING
                    || target == ReportTaskStatus.EXECUTING || target == ReportTaskStatus.REVIEWING
                    || target == ReportTaskStatus.EXPORTING || target == ReportTaskStatus.CANCELLED;
            case SUCCEEDED, FAILED, CANCELLED -> false;
        };
    }
}
