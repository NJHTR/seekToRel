package com.reportai.common.contract;

import com.reportai.common.tenant.TenantId;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** Append-only event envelope. Sequence zero means the storage adapter has not assigned it yet. */
public record EventEnvelope<T>(int schemaVersion, EventId eventId, String eventType,
                               TenantId tenantId, TypedId aggregateId, long aggregateVersion,
                               long eventSequence, CommandId causationId, CorrelationId correlationId,
                               TraceMetadata trace, Instant occurredAt, T payload,
                               Sha256Digest payloadDigest, Map<String, String> metadata) {
    public EventEnvelope {
        if (schemaVersion <= 0) throw new IllegalArgumentException("event schemaVersion must be positive");
        eventId = Objects.requireNonNull(eventId, "eventId");
        eventType = IdentifierRules.requireVersion(eventType, "eventType");
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        if (aggregateVersion < 0 || eventSequence < 0) {
            throw new IllegalArgumentException("event versions must not be negative");
        }
        correlationId = Objects.requireNonNull(correlationId, "correlationId");
        trace = Objects.requireNonNull(trace, "trace");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        payload = Objects.requireNonNull(payload, "payload");
        payloadDigest = Objects.requireNonNull(payloadDigest, "payloadDigest");
        metadata = CommandEnvelopeMetadata.copy(metadata, "event");
    }

    /** Shared bounded map validation without coupling the event to a transport. */
    static final class CommandEnvelopeMetadata {
        private CommandEnvelopeMetadata() { }

        static Map<String, String> copy(Map<String, String> source, String kind) {
            if (source == null || source.isEmpty()) return Map.of();
            if (source.size() > 32) throw new IllegalArgumentException(kind + " metadata is too large");
            java.util.Map<String, String> copy = new java.util.TreeMap<>();
            source.forEach((key, value) -> {
                if (key == null || !key.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")
                        || value == null || value.length() > 512
                        || value.chars().anyMatch(Character::isISOControl)) {
                    throw new IllegalArgumentException(kind + " metadata is invalid");
                }
                copy.put(key, value);
            });
            return Map.copyOf(copy);
        }
    }
}
