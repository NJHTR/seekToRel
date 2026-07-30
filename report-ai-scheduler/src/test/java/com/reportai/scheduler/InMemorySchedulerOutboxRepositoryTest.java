package com.reportai.scheduler;

import com.reportai.common.tenant.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemorySchedulerOutboxRepositoryTest {
    @Test
    void preservesBusinessIdempotencyAndSupportsLeaseRecovery() {
        InMemorySchedulerOutboxRepository repository = new InMemorySchedulerOutboxRepository();
        Instant created = Instant.now();
        QueuedTask first = task("task-1", "same-key", created);
        QueuedTask duplicate = task("task-2", "same-key", created.plusMillis(1));

        SchedulerOutboxEntry persisted = repository.enqueue(first);
        assertEquals("task-1", repository.enqueue(duplicate).id());

        SchedulerOutboxEntry claimed = repository.claimReady(created, "worker-a", Duration.ofSeconds(1), 1)
                .get(0);
        assertEquals(persisted.id(), claimed.id());
        assertTrue(repository.markPublished(claimed.id(), "worker-a", created.plusMillis(1)));
        assertTrue(repository.claimReady(created.plusSeconds(2), "worker-b", Duration.ofSeconds(1), 1).isEmpty());
        assertEquals(1, repository.purgePublished(created.plusSeconds(1), 1));
    }

    private static QueuedTask task(String id, String key, Instant created) {
        return new QueuedTask(id, TenantId.of("tenant-a"), "report-task", "{}", key,
                new RetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(5)), created);
    }
}
