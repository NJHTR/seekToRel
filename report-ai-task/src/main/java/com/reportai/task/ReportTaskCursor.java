package com.reportai.task;

import java.time.Instant;
import java.util.Objects;

/** Exclusive cursor for newest-first report-task listing. */
public record ReportTaskCursor(Instant createdAt, String taskId) {
    public ReportTaskCursor {
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        if (taskId == null || !taskId.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException("taskId is invalid");
        }
    }
}
