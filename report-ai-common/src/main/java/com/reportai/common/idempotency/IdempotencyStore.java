package com.reportai.common.idempotency;

import com.reportai.common.tenant.TenantId;

import java.util.Optional;

public interface IdempotencyStore {
    Optional<IdempotencyRecord> find(TenantId tenantId, String operation, IdempotencyKey key);

    /** Must be atomic for the tuple (tenantId, operation, key). */
    boolean reserve(IdempotencyRecord record);

    void complete(TenantId tenantId, String operation, IdempotencyKey key);

    void release(TenantId tenantId, String operation, IdempotencyKey key);
}
