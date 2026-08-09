package com.reportai.api.report;

import java.util.List;

/** Keyset page for the task observatory. The cursor is opaque and task-bound. */
public record ProgressEventHistoryResponse(List<ProgressEventResponse> events,
                                           String nextCursor,
                                           boolean hasMore) {
    public ProgressEventHistoryResponse {
        events = events == null ? List.of() : List.copyOf(events);
        if (!hasMore) {
            nextCursor = null;
        }
    }
}
