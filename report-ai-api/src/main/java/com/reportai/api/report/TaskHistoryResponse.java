package com.reportai.api.report;

import java.util.List;

/** Newest-first keyset page for the task archive. */
public record TaskHistoryResponse(List<TaskSummaryResponse> tasks, String nextCursor,
                                  boolean hasMore) {
    public TaskHistoryResponse {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        if (!hasMore) {
            nextCursor = null;
        }
    }
}
