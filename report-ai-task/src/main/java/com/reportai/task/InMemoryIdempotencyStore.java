package com.reportai.task;

import com.reportai.common.idempotency.IdempotencyKey;
import com.reportai.common.idempotency.IdempotencyRecord;
import com.reportai.common.idempotency.IdempotencyStore;
import com.reportai.common.tenant.TenantId;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Test/development adapter only. Production must use a unique DB constraint or atomic Redis command. */
public final class InMemoryIdempotencyStore implements IdempotencyStore {
    private final ConcurrentMap<String, IdempotencyRecord> records = new ConcurrentHashMap<>();

    @Override
    public Optional<IdempotencyRecord> find(TenantId tenantId, String operation, IdempotencyKey key) {
        String mapKey = mapKey(tenantId, operation, key);
        IdempotencyRecord record = records.get(mapKey);
        if (record != null && record.expiresAt().isBefore(Instant.now())) {
            records.remove(mapKey, record);
            return Optional.empty();
        }
        return Optional.ofNullable(record);
    }

    @Override
    public boolean reserve(IdempotencyRecord record) {
        return records.putIfAbsent(mapKey(record.tenantId(), record.operation(), record.key()), record) == null;
    }

    @Override
    public void complete(TenantId tenantId, String operation, IdempotencyKey key) {
        records.computeIfPresent(mapKey(tenantId, operation, key), (ignored, record) ->
                new IdempotencyRecord(record.tenantId(), record.operation(), record.key(), record.requestHash(),
                        record.resourceId(), IdempotencyRecord.Status.COMPLETED, record.expiresAt()));
    }

    @Override
    public void release(TenantId tenantId, String operation, IdempotencyKey key) {
        records.remove(mapKey(tenantId, operation, key));
    }

    private static String mapKey(TenantId tenantId, String operation, IdempotencyKey key) {
        return tenantId.value() + ':' + operation + ':' + key.value();
    }
}
