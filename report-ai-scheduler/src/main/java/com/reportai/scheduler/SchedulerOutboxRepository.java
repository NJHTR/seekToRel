package com.reportai.scheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Durable transactional-outbox port for scheduler submissions. */
public interface SchedulerOutboxRepository {
    /** Inserts a message, preserving the existing message for the same business idempotency key. */
    SchedulerOutboxEntry enqueue(QueuedTask task);

    /** Claims ready messages using a lease so multiple application instances can reconcile safely. */
    List<SchedulerOutboxEntry> claimReady(Instant now, String owner, Duration leaseDuration, int limit);

    boolean markPublished(String id, String owner, Instant now);

    boolean reschedule(String id, String owner, Instant now, Instant nextAttemptAt, String error);

    int purgePublished(Instant before, int limit);
}
