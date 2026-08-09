package com.reportai.api.report;

public record TaskResponse(String taskId, String status, long version, String manifestDigest) {
    public TaskResponse(String taskId, String status, long version) {
        this(taskId, status, version, null);
    }
}
