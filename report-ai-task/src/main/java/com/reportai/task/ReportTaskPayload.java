package com.reportai.task;

import com.reportai.common.tenant.TenantId;

import java.util.Map;

/**
 * Durable scheduler payload for a report task.  The payload deliberately contains
 * only tenant-scoped identifiers and immutable object metadata; it never contains
 * credentials or a host filesystem path.
 */
public record ReportTaskPayload(String taskId,
                                TenantId tenantId,
                                String documentId,
                                String documentObjectKey,
                                String mediaType,
                                long size,
                                String sha256,
                                String workflowDefinitionId,
                                String workspaceId,
                                String language,
                                Map<String, Object> runtimeRequest) {
    public ReportTaskPayload {
        requireId(taskId, "taskId");
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (documentId != null) {
            requireId(documentId, "documentId");
        }
        if (documentObjectKey == null || documentObjectKey.isBlank() || documentObjectKey.length() > 1024
                || documentObjectKey.indexOf('\\') >= 0 || documentObjectKey.contains("..")
                || documentObjectKey.startsWith("/")) {
            throw new IllegalArgumentException("documentObjectKey is invalid");
        }
        if (mediaType != null && mediaType.length() > 128) {
            throw new IllegalArgumentException("mediaType is invalid");
        }
        if (size < -1) {
            throw new IllegalArgumentException("size must be -1 or non-negative");
        }
        if (sha256 != null && (sha256.length() != 64 || !sha256.matches("[0-9a-fA-F]{64}"))) {
            throw new IllegalArgumentException("sha256 is invalid");
        }
        if (workflowDefinitionId == null || workflowDefinitionId.isBlank() || workflowDefinitionId.length() > 128) {
            workflowDefinitionId = "report";
        }
        if (workspaceId != null && (workspaceId.isBlank() || workspaceId.length() > 128
                || !workspaceId.matches("[A-Za-z0-9_-]+"))) {
            throw new IllegalArgumentException("workspaceId is invalid");
        }
        if (language != null && (language.isBlank() || language.length() > 32
                || !language.matches("[A-Za-z0-9_+#.-]+"))) {
            throw new IllegalArgumentException("language is invalid");
        }
        runtimeRequest = runtimeRequest == null ? Map.of() : Map.copyOf(runtimeRequest);
    }

    private static void requireId(String value, String field) {
        if (value == null || !value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}")) {
            throw new IllegalArgumentException(field + " is invalid");
        }
    }
}
