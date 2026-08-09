package com.reportai.common.idempotency;

import com.reportai.common.tenant.TenantId;

import java.time.Instant;

public record IdempotencyRecord(TenantId tenantId, String operation, IdempotencyKey key,
                                String requestHash, String resourceId, Status status, Instant expiresAt) {
    public enum Status { RESERVED, COMPLETED, FAILED }

    public IdempotencyRecord {
        if (tenantId == null || operation == null || operation.isBlank() || key == null
                || requestHash == null || requestHash.isBlank() || resourceId == null || resourceId.isBlank()
                || status == null || expiresAt == null) {
            throw new IllegalArgumentException("invalid idempotency record");
        }
    }
}
