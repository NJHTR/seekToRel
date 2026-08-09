package com.reportai.api.report;

import java.time.Instant;

/** Tenant-safe summary used by the Nexus task archive. */
public record TaskSummaryResponse(String taskId, String status, long version,
                                  Instant createdAt, Instant updatedAt, String documentId,
                                  String manifestDigest) {
    public TaskSummaryResponse(String taskId, String status, long version,
                                Instant createdAt, Instant updatedAt, String documentId) {
        this(taskId, status, version, createdAt, updatedAt, documentId, null);
    }
}
