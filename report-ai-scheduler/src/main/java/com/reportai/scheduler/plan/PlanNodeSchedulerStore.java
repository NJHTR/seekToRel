package com.reportai.scheduler.plan;

import com.reportai.common.tenant.TenantId;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persistence boundary for validated plan nodes. Implementations must make all transitions conditional
 * on the stored version and persist ordering/resource fields with the node row.
 */
public interface PlanNodeSchedulerStore {
    PlanNodeSubmitResult submit(PlanNodeSubmission submission, Instant now);

    default PlanNodeSubmitResult submit(PlanNodeSubmission submission) {
        return submit(submission, Instant.now());
    }

    default PlanNodeSubmitResult enqueue(PlanNodeSubmission submission, Instant now) {
        return submit(submission, now);
    }

    Optional<PlanNode> find(PlanNodeRef ref, TenantId tenantId);

    Optional<PlanNode> claim(PlanNodeRef ref, String owner, long expectedVersion,
                             Instant now, Duration leaseDuration);

    Optional<PlanNode> claimNext(String owner, Instant now, Duration leaseDuration);

    boolean renewLease(PlanNodeRef ref, String owner, long expectedVersion,
                       Instant now, Duration leaseDuration);

    boolean releaseLease(PlanNodeRef ref, String owner, long expectedVersion, Instant now);

    boolean markRunning(PlanNodeRef ref, String owner, long expectedVersion, Instant now);

    boolean markSucceeded(PlanNodeRef ref, String owner, long expectedVersion, Instant now);

    boolean markFailed(PlanNodeRef ref, String owner, long expectedVersion, Instant now);

    int releaseExpiredLeases(Instant now);

    /** Reconciles persisted claims after a process restart; no local round-robin state is consulted. */
    int reconcileAfterRestart(Instant now);

    PlanNodeCancelResult requestCancellation(PlanNodeCancelCommand command);

    Optional<PlanNode> acknowledgeCancellation(PlanNodeRef ref, String owner,
                                                long expectedVersion, Instant now);

    default Optional<PlanNode> acknowledgeCancellation(PlanNodeRef ref, long expectedVersion, Instant now) {
        return acknowledgeCancellation(ref, "", expectedVersion, now);
    }

    ResourceUsage reservedResources();

    default List<PlanNode> snapshot() {
        return List.of();
    }
}
