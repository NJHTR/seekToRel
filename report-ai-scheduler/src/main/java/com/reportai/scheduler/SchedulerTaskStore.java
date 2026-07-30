package com.reportai.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Persistence boundary. Implementations must use conditional updates/unique keys;
 * the scheduler never assumes that an in-memory claim is durable.
 */
public interface SchedulerTaskStore {
    SubmissionResult enqueue(QueuedTask task);

    Optional<ScheduledTask> findById(String tenantId, String taskId);

    Optional<ScheduledTask> claim(String taskId, String workerId, Instant now, Duration leaseDuration);

    Optional<ScheduledTask> claimNext(Instant now, String workerId, Duration leaseDuration);

    boolean renewLease(String taskId, String workerId, long version, Instant now, Duration leaseDuration);

    /** Releases a claim when dispatch cannot be accepted by the bounded worker pool. */
    boolean releaseLease(String taskId, String workerId, long version, Instant now);

    boolean markSucceeded(String taskId, String workerId, long version, Instant now);

    FailureTransition markFailure(String taskId, String workerId, long version, Instant now,
                                  boolean retryable, String errorMessage);

    boolean cancel(String taskId, String tenantId, Instant now);

    int releaseExpiredLeases(Instant now);
}
