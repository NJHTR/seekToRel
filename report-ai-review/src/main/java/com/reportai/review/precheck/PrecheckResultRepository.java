package com.reportai.review.precheck;

import com.reportai.common.contract.Sha256Digest;
import com.reportai.common.tenant.TenantId;
import java.util.Optional;

public interface PrecheckResultRepository {
    Optional<PrecheckResult> findByInput(TenantId tenantId, Sha256Digest inputDigest, String operation, String rulePackId, String rulePackVersion);
    void save(PrecheckResult result);
}
