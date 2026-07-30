package com.reportai.api.report;

public record TaskResponse(String taskId, String status, long version) {
}
