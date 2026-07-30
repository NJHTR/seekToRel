package com.reportai.task;

import com.reportai.common.idempotency.IdempotencyKey;
import com.reportai.common.tenant.TenantId;

public record CreateReportTaskCommand(TenantId tenantId, String documentObjectKey, IdempotencyKey idempotencyKey) {
    public CreateReportTaskCommand {
        if (tenantId == null || documentObjectKey == null || documentObjectKey.isBlank() || idempotencyKey == null) {
            throw new IllegalArgumentException("tenantId, documentObjectKey and idempotencyKey are required");
        }
        if (documentObjectKey.length() > 512 || documentObjectKey.contains("..")
                || documentObjectKey.indexOf('\\') >= 0 || documentObjectKey.startsWith("/")) {
            throw new IllegalArgumentException("invalid document object key");
        }
    }
}
