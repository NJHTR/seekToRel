package com.reportai.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Test/development adapter. Production must implement the same conditional transitions in PostgreSQL. */
public final class InMemorySchedulerTaskStore implements SchedulerTaskStore {
    private final Map<String, ScheduledTask> tasks = new ConcurrentHashMap<>();
    private final Map<String, String> idempotency = new ConcurrentHashMap<>();
    private final Map<String, String> requestHashes = new ConcurrentHashMap<>();

    @Override
    public synchronized SubmissionResult enqueue(QueuedTask input) {
        String idempotencyKey = idempotencyKey(input.tenantId().value(), input.type(), input.idempotencyKey());
        String existingId = idempotency.get(idempotencyKey);
        if (existingId != null) {
            if (!input.requestHash().equals(requestHashes.get(idempotencyKey))) {
                throw new SchedulerIdempotencyConflictException();
            }
            return new SubmissionResult(tasks.get(existingId), false);
        }
        Instant now = input.createdAt();
        ScheduledTask task = new ScheduledTask(input.id(), input.tenantId(), input.type(), input.payload(),
                input.idempotencyKey(), input.retryPolicy(), 0, SchedulerTaskStatus.QUEUED, now,
                null, null, 0, now, now);
        tasks.put(task.id(), task);
        idempotency.put(idempotencyKey, task.id());
        requestHashes.put(idempotencyKey, input.requestHash());
        return new SubmissionResult(task, true);
    }

    @Override
    public Optional<ScheduledTask> findById(String tenantId, String taskId) {
        return Optional.ofNullable(tasks.get(taskId)).filter(task -> task.tenantId().value().equals(tenantId));
    }

    @Override
    public synchronized Optional<ScheduledTask> claim(String taskId, String workerId, Instant now,
                                                       Duration leaseDuration) {
        ScheduledTask current = tasks.get(taskId);
        if (current == null || !isClaimable(current, now)) {
            return Optional.empty();
        }
        ScheduledTask claimed = claim(current, workerId, now, leaseDuration);
        tasks.put(taskId, claimed);
        return Optional.of(claimed);
    }

    @Override
    public synchronized Optional<ScheduledTask> claimNext(Instant now, String workerId, Duration leaseDuration) {
        return tasks.values().stream()
                .filter(task -> isClaimable(task, now))
                .min(Comparator.comparing(ScheduledTask::nextRunAt).thenComparing(ScheduledTask::createdAt))
                .map(task -> {
                    ScheduledTask claimed = claim(task, workerId, now, leaseDuration);
                    tasks.put(task.id(), claimed);
                    return claimed;
                });
    }

    private static boolean isClaimable(ScheduledTask task, Instant now) {
        return (task.status() == SchedulerTaskStatus.QUEUED || task.status() == SchedulerTaskStatus.RETRY_WAITING)
                && !task.nextRunAt().isAfter(now);
    }

    private static ScheduledTask claim(ScheduledTask current, String workerId, Instant now, Duration leaseDuration) {
        return new ScheduledTask(current.id(), current.tenantId(), current.type(), current.payload(),
                current.idempotencyKey(), current.retryPolicy(), current.attempt() + 1,
                SchedulerTaskStatus.RUNNING, current.nextRunAt(), workerId, now.plus(leaseDuration),
                current.version() + 1, current.createdAt(), now);
    }

    @Override
    public synchronized boolean renewLease(String taskId, String workerId, long version, Instant now,
                                            Duration leaseDuration) {
        ScheduledTask current = tasks.get(taskId);
        if (!ownedRunning(current, workerId, version)) {
            return false;
        }
        tasks.put(taskId, withLease(current, now.plus(leaseDuration), now));
        return true;
    }

    @Override
    public synchronized boolean releaseLease(String taskId, String workerId, long version, Instant now) {
        ScheduledTask current = tasks.get(taskId);
        if (!ownedRunning(current, workerId, version)) {
            return false;
        }
        tasks.put(taskId, new ScheduledTask(current.id(), current.tenantId(), current.type(), current.payload(),
                current.idempotencyKey(), current.retryPolicy(), current.attempt(), SchedulerTaskStatus.QUEUED,
                now, null, null, current.version() + 1, current.createdAt(), now));
        return true;
    }

    private static ScheduledTask withLease(ScheduledTask task, Instant leaseUntil, Instant now) {
        return new ScheduledTask(task.id(), task.tenantId(), task.type(), task.payload(), task.idempotencyKey(),
                task.retryPolicy(), task.attempt(), task.status(), task.nextRunAt(), task.leaseOwner(), leaseUntil,
                task.version() + 1, task.createdAt(), now);
    }

    private static boolean ownedRunning(ScheduledTask task, String workerId, long version) {
        return task != null && task.status() == SchedulerTaskStatus.RUNNING && task.version() == version
                && workerId.equals(task.leaseOwner());
    }

    @Override
    public synchronized boolean markSucceeded(String taskId, String workerId, long version, Instant now) {
        ScheduledTask current = tasks.get(taskId);
        if (!ownedRunning(current, workerId, version)) {
            return false;
        }
        tasks.put(taskId, new ScheduledTask(current.id(), current.tenantId(), current.type(), current.payload(),
                current.idempotencyKey(), current.retryPolicy(), current.attempt(), SchedulerTaskStatus.SUCCEEDED,
                current.nextRunAt(), null, null, current.version() + 1, current.createdAt(), now));
        return true;
    }

    @Override
    public synchronized FailureTransition markFailure(String taskId, String workerId, long version, Instant now,
                                                       boolean retryable, String errorMessage) {
        ScheduledTask current = tasks.get(taskId);
        if (!ownedRunning(current, workerId, version)) {
            return new FailureTransition(SchedulerTaskStatus.FAILED, false, version);
        }
        boolean retry = retryable && current.attempt() < current.retryPolicy().maxAttempts();
        SchedulerTaskStatus status = retry ? SchedulerTaskStatus.RETRY_WAITING : SchedulerTaskStatus.FAILED;
        Duration backoff = retry ? current.retryPolicy().backoffForAttempt(current.attempt()) : Duration.ZERO;
        Instant next = retry ? now.plus(backoff) : current.nextRunAt();
        tasks.put(taskId, new ScheduledTask(current.id(), current.tenantId(), current.type(), current.payload(),
                current.idempotencyKey(), current.retryPolicy(), current.attempt(), status, next, null, null,
                current.version() + 1, current.createdAt(), now));
        return new FailureTransition(status, retry, current.version() + 1);
    }

    @Override
    public synchronized boolean cancel(String taskId, String tenantId, Instant now) {
        ScheduledTask current = tasks.get(taskId);
        if (current == null || !current.tenantId().value().equals(tenantId)
                || current.status() == SchedulerTaskStatus.SUCCEEDED || current.status() == SchedulerTaskStatus.FAILED
                || current.status() == SchedulerTaskStatus.CANCELLED) {
            return false;
        }
        tasks.put(taskId, new ScheduledTask(current.id(), current.tenantId(), current.type(), current.payload(),
                current.idempotencyKey(), current.retryPolicy(), current.attempt(), SchedulerTaskStatus.CANCELLED,
                current.nextRunAt(), null, null, current.version() + 1, current.createdAt(), now));
        return true;
    }

    @Override
    public synchronized int releaseExpiredLeases(Instant now) {
        List<ScheduledTask> expired = new ArrayList<>();
        for (ScheduledTask task : tasks.values()) {
            if (task.status() == SchedulerTaskStatus.RUNNING && task.leaseUntil() != null
                    && !task.leaseUntil().isAfter(now)) {
                expired.add(task);
            }
        }
        for (ScheduledTask task : expired) {
            tasks.put(task.id(), new ScheduledTask(task.id(), task.tenantId(), task.type(), task.payload(),
                    task.idempotencyKey(), task.retryPolicy(), task.attempt(), SchedulerTaskStatus.QUEUED, now,
                    null, null, task.version() + 1, task.createdAt(), now));
        }
        return expired.size();
    }

    private static String idempotencyKey(String tenant, String type, String key) {
        return tenant + '\u0000' + type + '\u0000' + key;
    }
}
