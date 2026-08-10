package com.reportai.scheduler.plan;

import com.reportai.common.contract.Sha256Digest;
import com.reportai.common.contract.InputSnapshot;
import com.reportai.common.tenant.TenantId;

import java.time.Instant;
import java.util.Objects;

/**
 * Immutable, short-lived proof that an input snapshot passed program validation.
 * The scheduler deliberately knows only the digest and decision, never the rule implementation.
 */
public record PrecheckAttestation(String attestationId, TenantId tenantId,
                                  Sha256Digest snapshotDigest, Sha256Digest attestationDigest,
                                  boolean accepted, Instant issuedAt, Instant expiresAt) {
    public PrecheckAttestation {
        attestationId = text(attestationId, "attestationId", 128);
        tenantId = Objects.requireNonNull(tenantId, "tenantId");
        snapshotDigest = Objects.requireNonNull(snapshotDigest, "snapshotDigest");
        attestationDigest = Objects.requireNonNull(attestationDigest, "attestationDigest");
        issuedAt = Objects.requireNonNull(issuedAt, "issuedAt");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt");
        if (!expiresAt.isAfter(issuedAt)) {
            throw new IllegalArgumentException("attestation expiry must be after issue time");
        }
    }

    public PrecheckAttestation(String attestationId, TenantId tenantId,
                               Sha256Digest snapshotDigest, boolean accepted,
                               Instant issuedAt, Instant expiresAt) {
        this(attestationId, tenantId, snapshotDigest,
                Sha256Digest.ofText(attestationId + "\u0000" + tenantId.value() + "\u0000"
                        + snapshotDigest.value() + "\u0000" + accepted + "\u0000"
                        + issuedAt + "\u0000" + expiresAt),
                accepted, issuedAt, expiresAt);
    }

    public boolean isValidFor(InputSnapshot snapshot, TenantId expectedTenant, Instant at) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(expectedTenant, "expectedTenant");
        Objects.requireNonNull(at, "at");
        return accepted && tenantId.equals(expectedTenant) && snapshot.tenantId().equals(expectedTenant)
                && snapshotDigest.equals(snapshot.contentDigest())
                && !at.isBefore(issuedAt) && at.isBefore(expiresAt);
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
