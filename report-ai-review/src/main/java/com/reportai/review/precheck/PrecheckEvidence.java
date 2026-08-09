package com.reportai.review.precheck;

import com.reportai.common.contract.EvidenceRef;
import com.reportai.common.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;

public record PrecheckEvidence(EvidenceRef reference, TenantId tenantId, boolean available,
                               Instant expiresAt, String schemaVersion) {
    public PrecheckEvidence {
        reference = Objects.requireNonNull(reference, "reference"); tenantId = Objects.requireNonNull(tenantId, "tenantId");
        expiresAt = Objects.requireNonNull(expiresAt, "expiresAt"); schemaVersion = Objects.requireNonNull(schemaVersion, "schemaVersion");
        if (schemaVersion.isBlank() || schemaVersion.length() > 32 || schemaVersion.chars().anyMatch(Character::isISOControl)) throw new IllegalArgumentException("schemaVersion is invalid");
    }
}
