package com.reportai.review.precheck;

import com.reportai.common.contract.EvidenceRef;
import com.reportai.common.tenant.TenantId;
import java.util.Optional;

@FunctionalInterface
public interface PrecheckEvidenceResolver {
    Optional<PrecheckEvidence> resolve(TenantId tenantId, EvidenceRef reference);
}
