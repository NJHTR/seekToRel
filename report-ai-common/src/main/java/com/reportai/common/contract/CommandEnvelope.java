package com.reportai.common.contract;

import com.reportai.common.idempotency.IdempotencyKey;
import com.reportai.common.tenant.TenantId;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Provider-neutral idempotent command envelope. Payload encoding belongs to an adapter. */
public record CommandEnvelope<T>(int schemaVersion, CommandId commandId, String commandType,
                                 TenantId tenantId, TypedId aggregateId, IdempotencyKey idempotencyKey,
                                 CorrelationId correlationId, TraceMetadata trace, Instant issuedAt,
                                 T payload, Sha256Digest payloadDigest, Map<String, String> metadata) {
    public CommandEnvelope {
        if (schemaVersion <= 0) throw new IllegalArgumentException("command schemaVersion must be positive");
        commandId = Objects.requireNonNull(commandId, "commandId");
        commandType = IdentifierRules.requireVersion(commandType, "commandType");
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        aggregateId = Objects.requireNonNull(aggregateId, "aggregateId");
        idempotencyKey = Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        correlationId = Objects.requireNonNull(correlationId, "correlationId");
        trace = Objects.requireNonNull(trace, "trace");
        issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        payload = Objects.requireNonNull(payload, "payload");
        payloadDigest = Objects.requireNonNull(payloadDigest, "payloadDigest");
        metadata = boundedMetadata(metadata);
    }

    private static Map<String, String> boundedMetadata(Map<String, String> source) {
        if (source == null || source.isEmpty()) return Map.of();
        if (source.size() > 32) throw new IllegalArgumentException("command metadata is too large");
        java.util.Map<String, String> copy = new java.util.TreeMap<>();
        source.forEach((key, value) -> {
            if (key == null || !key.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")
                    || value == null || value.length() > 512
                    || value.chars().anyMatch(Character::isISOControl)) {
                throw new IllegalArgumentException("command metadata is invalid");
            }
            copy.put(key, value);
        });
        return Map.copyOf(copy);
    }
}
