package com.reportai.task;

import com.reportai.common.exception.BusinessException;
import com.reportai.common.api.ErrorCode;
import com.reportai.common.tenant.TenantId;

import java.time.Instant;

public record ReportTask(String id, TenantId tenantId, String documentObjectKey,
                         ReportTaskStatus status, long version, Instant createdAt, Instant updatedAt,
                         String manifestDigest) {
    /** Compatibility constructor for tasks created before manifest locking was introduced. */
    public ReportTask(String id, TenantId tenantId, String documentObjectKey,
                      ReportTaskStatus status, long version, Instant createdAt, Instant updatedAt) {
        this(id, tenantId, documentObjectKey, status, version, createdAt, updatedAt, null);
    }

    public ReportTask {
        if (id == null || id.isBlank() || tenantId == null || documentObjectKey == null || documentObjectKey.isBlank()
                || status == null || version < 0 || createdAt == null || updatedAt == null) {
            throw new IllegalArgumentException("invalid report task");
        }
        if (manifestDigest != null && !manifestDigest.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("manifestDigest must be a SHA-256 hex value");
        }
        manifestDigest = manifestDigest == null ? null : manifestDigest.toLowerCase(java.util.Locale.ROOT);
    }

    /** Stable document identity encoded in the tenant-scoped source object key. */
    public String documentId() {
        int slash = documentObjectKey.lastIndexOf('/');
        String fileName = slash < 0 ? documentObjectKey : documentObjectKey.substring(slash + 1);
        if (!fileName.endsWith(".docx")) {
            return null;
        }
        String value = fileName.substring(0, fileName.length() - ".docx".length());
        return value.matches("[A-Za-z0-9][A-Za-z0-9._:-]{0,127}") ? value : null;
    }

    public ReportTask transitionTo(ReportTaskStatus target) {
        if (!isAllowed(status, target)) {
            throw new BusinessException(ErrorCode.CONFLICT, "task cannot transition from " + status + " to " + target);
        }
        Instant now = Instant.now();
        return new ReportTask(id, tenantId, documentObjectKey, target, version + 1, createdAt, now,
                manifestDigest);
    }

    /**
     * Locks the task to the first resolved environment manifest. A retry may
     * write the same digest, but a different manifest can never replace it.
     */
    public ReportTask lockManifestDigest(String digest) {
        if (digest == null || !digest.matches("[0-9a-fA-F]{64}")) {
            throw new IllegalArgumentException("manifestDigest must be a SHA-256 hex value");
        }
        String normalized = digest.toLowerCase(java.util.Locale.ROOT);
        if (manifestDigest != null) {
            if (!manifestDigest.equals(normalized)) {
                throw new IllegalStateException("manifestDigest is immutable");
            }
            return this;
        }
        return new ReportTask(id, tenantId, documentObjectKey, status, version + 1,
                createdAt, Instant.now(), normalized);
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
