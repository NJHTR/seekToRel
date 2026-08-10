package com.reportai.scheduler.plan;

import com.reportai.common.contract.InputSnapshot;
import com.reportai.common.contract.Sha256Digest;

import java.time.Instant;
import java.util.Objects;

/** Immutable persisted scheduler row; all fields needed for restart ordering are retained here. */
public record PlanNode(PlanNodeRef ref, InputSnapshot inputSnapshot, PrecheckAttestation attestation,
                       Priority priority, FairnessKey fairnessKey, ResourceRequest resourceRequest,
                       String idempotencyKey, Sha256Digest requestDigest, PlanNodeStatus status,
                       PlanNodeLease lease, ResourceReservation reservation,
                       String cancellationIdempotencyKey, Sha256Digest cancellationRequestDigest,
                       long version, Instant createdAt, Instant updatedAt) {
    public PlanNode {
        ref = Objects.requireNonNull(ref, "ref");
        inputSnapshot = Objects.requireNonNull(inputSnapshot, "inputSnapshot");
        attestation = Objects.requireNonNull(attestation, "attestation");
        priority = Objects.requireNonNull(priority, "priority");
        fairnessKey = Objects.requireNonNull(fairnessKey, "fairnessKey");
        resourceRequest = Objects.requireNonNull(resourceRequest, "resourceRequest");
        idempotencyKey = text(idempotencyKey, "idempotencyKey", 128);
        requestDigest = Objects.requireNonNull(requestDigest, "requestDigest");
        if (!requestDigest.equals(PlanNodeSubmission.computeDigest(ref, inputSnapshot, attestation,
                priority, fairnessKey, resourceRequest, idempotencyKey))) {
            throw new IllegalArgumentException("node request digest does not match immutable fields");
        }
        status = Objects.requireNonNull(status, "status");
        if (version < 0) throw new IllegalArgumentException("version must not be negative");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (!ref.tenantId().equals(inputSnapshot.tenantId()) || !ref.tenantId().equals(attestation.tenantId())) {
            throw new IllegalArgumentException("node tenant does not match input attestation");
        }
        if (cancellationIdempotencyKey == null ^ cancellationRequestDigest == null) {
            throw new IllegalArgumentException("cancellation idempotency fields must be paired");
        }
        if (cancellationIdempotencyKey != null) cancellationIdempotencyKey = text(cancellationIdempotencyKey, "cancellationIdempotencyKey", 128);
        if (status == PlanNodeStatus.READY && (lease != null || reservation != null)) {
            throw new IllegalArgumentException("ready node cannot retain a lease");
        }
        if (status == PlanNodeStatus.CLAIMED || status == PlanNodeStatus.RUNNING) {
            if (lease == null || reservation == null) throw new IllegalArgumentException("claimed node requires reservation");
        }
        if (status == PlanNodeStatus.CANCELLATION_REQUESTED && (lease != null ^ reservation != null)) {
            throw new IllegalArgumentException("cancellation lease and reservation must be paired");
        }
        if (status == PlanNodeStatus.SUCCEEDED || status == PlanNodeStatus.FAILED || status == PlanNodeStatus.CANCELLED) {
            if (lease != null || reservation != null) throw new IllegalArgumentException("terminal node cannot retain a lease");
        }
    }

    public static PlanNode ready(PlanNodeSubmission submission, Instant now) {
        Objects.requireNonNull(submission, "submission");
        Objects.requireNonNull(now, "now");
        return new PlanNode(submission.ref(), submission.inputSnapshot(), submission.attestation(),
                submission.priority(), submission.fairnessKey(), submission.resourceRequest(),
                submission.idempotencyKey(), submission.requestDigest(), PlanNodeStatus.READY,
                null, null, null, null, 0, now, now);
    }

    public boolean claimableAt(Instant now) {
        return status == PlanNodeStatus.READY && lease == null && reservation == null;
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
