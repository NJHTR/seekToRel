package com.reportai.scheduler;

import com.reportai.common.tenant.TenantId;

import java.util.List;

/**
 * Durable, tenant-scoped progress history port. Implementations belong to infrastructure;
 * this scheduler contract deliberately has no Spring or JDBC dependency.
 */
public interface ProgressEventHistoryRepository {
    int MAX_PAGE_SIZE = 101;

    /** Idempotently appends an event. A duplicate event id is treated as already stored. */
    ProgressEventHistoryEntry append(TenantId tenantId, ProgressEvent event);

    /**
     * Reads events in ascending database-sequence order after an exclusive cursor. Callers
     * may request MAX_PAGE_SIZE to detect a next page without an offset scan.
     */
    List<ProgressEventHistoryEntry> listAfter(TenantId tenantId, String taskId,
                                              ProgressEventCursor after, int limit);
}
