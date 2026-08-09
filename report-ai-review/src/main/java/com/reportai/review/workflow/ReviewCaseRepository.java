package com.reportai.review.workflow;

import com.reportai.common.tenant.TenantId;

import java.util.List;
import java.util.Optional;

/** Persistence boundary for the aggregate; implementations must provide atomic version checks. */
public interface ReviewCaseRepository {
    ReviewCase create(ReviewCase reviewCase);
    Optional<ReviewCase> find(String caseId, TenantId tenantId);
    AppendResult append(ReviewCase expected, ReviewCase next, ReviewEvent event);
    List<ReviewEvent> events(String caseId, TenantId tenantId);

    default Optional<ReviewEvent> findByIdempotency(String caseId, TenantId tenantId, String idempotencyKey) {
        return events(caseId, tenantId).stream().filter(event -> event.idempotencyKey().equals(idempotencyKey)).findFirst();
    }

    record AppendResult(ReviewCase state, boolean duplicate) {
        public AppendResult {
            if (state == null) throw new NullPointerException("state");
        }
    }
}
