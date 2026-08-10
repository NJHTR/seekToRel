package com.reportai.scheduler.plan;

import com.reportai.common.contract.InputSnapshot;
import com.reportai.common.contract.Sha256Digest;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Command payload used to create a validated, ready plan node. */
public record PlanNodeSubmission(PlanNodeRef ref, InputSnapshot inputSnapshot,
                                 PrecheckAttestation attestation, Priority priority,
                                 FairnessKey fairnessKey, ResourceRequest resourceRequest,
                                 String idempotencyKey, Sha256Digest requestDigest) {
    public PlanNodeSubmission {
        ref = Objects.requireNonNull(ref, "ref");
        inputSnapshot = Objects.requireNonNull(inputSnapshot, "inputSnapshot");
        attestation = Objects.requireNonNull(attestation, "attestation");
        priority = Objects.requireNonNull(priority, "priority");
        fairnessKey = Objects.requireNonNull(fairnessKey, "fairnessKey");
        resourceRequest = Objects.requireNonNull(resourceRequest, "resourceRequest");
        idempotencyKey = text(idempotencyKey, "idempotencyKey", 128);
        requestDigest = Objects.requireNonNull(requestDigest, "requestDigest");
        if (!ref.tenantId().equals(inputSnapshot.tenantId())
                || !ref.tenantId().equals(attestation.tenantId())) {
            throw new IllegalArgumentException("tenant does not match input attestation");
        }
        if (!requestDigest.equals(computeDigest(ref, inputSnapshot, attestation, priority,
                fairnessKey, resourceRequest, idempotencyKey))) {
            throw new IllegalArgumentException("request digest does not match submission");
        }
    }

    public PlanNodeSubmission(PlanNodeRef ref, InputSnapshot inputSnapshot,
                              PrecheckAttestation attestation, Priority priority,
                              FairnessKey fairnessKey, ResourceRequest resourceRequest,
                              String idempotencyKey) {
        this(ref, inputSnapshot, attestation, priority, fairnessKey, resourceRequest,
                idempotencyKey, computeDigest(ref, inputSnapshot, attestation, priority,
                        fairnessKey, resourceRequest, idempotencyKey));
    }

    public static Sha256Digest computeDigest(PlanNodeRef ref, InputSnapshot snapshot,
                                              PrecheckAttestation attestation, Priority priority,
                                              FairnessKey fairness, ResourceRequest resources,
                                              String idempotencyKey) {
        String canonical = ref.durableKey() + '|' + snapshot.snapshotId().value() + '|'
                + snapshot.contentDigest().value() + '|' + attestation.attestationId() + '|'
                + attestation.attestationDigest().value() + '|' + priority.value() + '|'
                + fairness.tenantReadySequence() + '|' + fairness.globalReadySequence() + '|'
                + fairness.enqueuedAt() + '|' + resources.cpuMillis() + '|'
                + resources.memoryMiB() + '|' + resources.storageMiB() + '|'
                + resources.slots() + '|' + Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        return Sha256Digest.ofText(canonical);
    }

    public byte[] digestBytes() {
        return requestDigest.value().getBytes(StandardCharsets.US_ASCII);
    }

    private static String text(String value, String field, int max) {
        Objects.requireNonNull(value, field);
        String normalized = value.strip();
        if (normalized.isBlank() || normalized.length() > max
                || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return normalized;
    }
}
