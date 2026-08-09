package com.reportai.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/** Small deterministic adapter for tests and local non-persistent runs. */
public final class InMemorySchedulerOutboxRepository implements SchedulerOutboxRepository {
    private final Map<String, SchedulerOutboxEntry> entries = new ConcurrentHashMap<>();
    private final Map<String, String> businessKeys = new ConcurrentHashMap<>();

    @Override
    public synchronized SchedulerOutboxEntry enqueue(QueuedTask task) {
        Objects.requireNonNull(task, "task");
        String businessKey = task.tenantId().value() + "\u0000" + task.type()
                + "\u0000" + task.idempotencyKey();
        String existingId = businessKeys.get(businessKey);
        if (existingId != null) {
            return entries.get(existingId);
        }
        SchedulerOutboxEntry created = SchedulerOutboxEntry.pending(task);
        entries.put(task.id(), created);
        businessKeys.putIfAbsent(businessKey, task.id());
        return entries.get(businessKeys.get(businessKey));
    }

    @Override
    public synchronized List<SchedulerOutboxEntry> claimReady(Instant now, String owner,
                                                                Duration leaseDuration, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        List<SchedulerOutboxEntry> candidates = entries.values().stream()
                .filter(entry -> entry.status() == SchedulerOutboxEntry.OutboxStatus.PENDING
                        || (entry.status() == SchedulerOutboxEntry.OutboxStatus.IN_FLIGHT
                        && entry.leaseUntil() != null && !entry.leaseUntil().isAfter(now)))
                .filter(entry -> !entry.nextAttemptAt().isAfter(now))
                .sorted(Comparator.comparing(SchedulerOutboxEntry::nextAttemptAt)
                        .thenComparing(SchedulerOutboxEntry::createdAt))
                .limit(limit)
                .toList();
        Instant until = now.plus(leaseDuration);
        List<SchedulerOutboxEntry> claimed = new ArrayList<>(candidates.size());
        for (SchedulerOutboxEntry entry : candidates) {
            SchedulerOutboxEntry next = entry.claimed(owner, until, now);
            entries.put(entry.id(), next);
            claimed.add(next);
        }
        return claimed;
    }

    @Override
    public synchronized boolean markPublished(String id, String owner, Instant now) {
        SchedulerOutboxEntry entry = entries.get(id);
        if (entry == null || entry.status() != SchedulerOutboxEntry.OutboxStatus.IN_FLIGHT
                || !owner.equals(entry.leaseOwner())) {
            return false;
        }
        entries.put(id, new SchedulerOutboxEntry(entry.id(), entry.tenantId(), entry.task(),
                SchedulerOutboxEntry.OutboxStatus.PUBLISHED, entry.attempts(), entry.nextAttemptAt(),
                null, null, null, entry.createdAt(), now));
        return true;
    }

    @Override
    public synchronized boolean reschedule(String id, String owner, Instant now,
                                            Instant nextAttemptAt, String error) {
        SchedulerOutboxEntry entry = entries.get(id);
        if (entry == null || entry.status() != SchedulerOutboxEntry.OutboxStatus.IN_FLIGHT
                || !owner.equals(entry.leaseOwner())) {
            return false;
        }
        entries.put(id, new SchedulerOutboxEntry(entry.id(), entry.tenantId(), entry.task(),
                SchedulerOutboxEntry.OutboxStatus.PENDING, entry.attempts(), nextAttemptAt,
                null, null, error, entry.createdAt(), now));
        return true;
    }

    @Override
    public synchronized int purgePublished(Instant before, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        List<SchedulerOutboxEntry> removable = entries.values().stream()
                .filter(entry -> entry.status() == SchedulerOutboxEntry.OutboxStatus.PUBLISHED
                        && entry.updatedAt().isBefore(before))
                .limit(limit)
                .toList();
        for (SchedulerOutboxEntry entry : removable) {
            entries.remove(entry.id());
            businessKeys.remove(entry.tenantId().value() + "\u0000" + entry.task().type()
                    + "\u0000" + entry.task().idempotencyKey(), entry.id());
        }
        return removable.size();
    }
}
