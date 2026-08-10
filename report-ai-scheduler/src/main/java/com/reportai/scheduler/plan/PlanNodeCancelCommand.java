package com.reportai.scheduler.plan;

import com.reportai.common.contract.CommandId;
import com.reportai.common.contract.Sha256Digest;
import com.reportai.common.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;

/** Durable cancellation request. A request is not a cancellation acknowledgement. */
public record PlanNodeCancelCommand(CommandId commandId, String idempotencyKey, PlanNodeRef ref,
                                    TenantId tenantId, long expectedVersion, String reason,
                                    Sha256Digest requestDigest, Instant requestedAt) {
    public PlanNodeCancelCommand {
        commandId = Objects.requireNonNull(commandId, "commandId");
        idempotencyKey = text(idempotencyKey, "idempotencyKey", 128);
        ref = Objects.requireNonNull(ref, "ref");
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        if (!ref.tenantId().equals(tenantId)) throw new IllegalArgumentException("tenant does not match node");
        if (expectedVersion < 0) throw new IllegalArgumentException("expectedVersion must not be negative");
        reason = text(reason, "reason", 512);
        requestDigest = Objects.requireNonNull(requestDigest, "requestDigest");
        requestedAt = Objects.requireNonNull(requestedAt, "requestedAt");
        if (!requestDigest.equals(computeDigest(commandId, idempotencyKey, ref, tenantId,
                expectedVersion, reason))) throw new IllegalArgumentException("request digest mismatch");
    }

    public PlanNodeCancelCommand(CommandId commandId, String idempotencyKey, PlanNodeRef ref,
                                 TenantId tenantId, long expectedVersion, String reason,
                                 Instant requestedAt) {
        this(commandId, idempotencyKey, ref, tenantId, expectedVersion, reason,
                computeDigest(commandId, idempotencyKey, ref, tenantId, expectedVersion, reason), requestedAt);
    }

    public static Sha256Digest computeDigest(CommandId commandId, String idempotencyKey,
                                             PlanNodeRef ref, TenantId tenantId, long expectedVersion,
                                             String reason) {
        Objects.requireNonNull(commandId, "commandId");
        return Sha256Digest.ofText(idempotencyKey + '|'
                + ref.durableKey() + '|' + tenantId.value() + '|' + expectedVersion + '|' + reason);
    }

    private static String text(String value, String field, int max) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isBlank() || normalized.length() > max || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }
}
