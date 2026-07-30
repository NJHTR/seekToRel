package com.reportai.scheduler;

import com.reportai.common.tenant.TenantId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoundedTaskSchedulerTest {
    private static final TenantId TENANT = TenantId.of("tenant-a");

    @Test
    void duplicateIdempotencyKeyReturnsTheOriginalTask() {
        InMemorySchedulerTaskStore store = new InMemorySchedulerTaskStore();
        TaskHandlerRegistry registry = new TaskHandlerRegistry();
        registry.register("noop", context -> { });
        try (BoundedTaskScheduler scheduler = new BoundedTaskScheduler(store, registry, ProgressEventSink.noOp(),
                new SchedulerConfig(1, 2, Duration.ofSeconds(5), Duration.ofSeconds(5), 100))) {
            QueuedTask first = task("one", "same-key");
            QueuedTask duplicate = task("two", "same-key");
            SubmissionResult firstResult = scheduler.submit(first);
            SubmissionResult secondResult = scheduler.submit(duplicate);
            assertTrue(firstResult.created());
            assertTrue(!secondResult.created());
            assertEquals(first.id(), secondResult.task().id());
        }
    }

    @Test
    void duplicateKeyWithDifferentPayloadIsRejected() {
        InMemorySchedulerTaskStore store = new InMemorySchedulerTaskStore();
        store.enqueue(task("one", "same-key"));
        QueuedTask conflict = new QueuedTask("two", TENANT, "noop", "{\"different\":true}", "same-key",
                new RetryPolicy(1, Duration.ZERO, Duration.ZERO), Instant.now());
        assertThrows(SchedulerIdempotencyConflictException.class, () -> store.enqueue(conflict));
    }

    @Test
    void retryIsDurableAndRequiresASecondDispatchSignal() throws Exception {
        InMemorySchedulerTaskStore store = new InMemorySchedulerTaskStore();
        TaskHandlerRegistry registry = new TaskHandlerRegistry();
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch succeeded = new CountDownLatch(1);
        registry.register("retry", context -> {
            if (attempts.getAndIncrement() == 0) {
                throw new IllegalStateException("transient");
            }
            succeeded.countDown();
        });
        QueuedTask task = new QueuedTask("retry-1", TENANT, "retry", "{}", "key-1",
                new RetryPolicy(2, Duration.ZERO, Duration.ZERO), Instant.now());
        try (BoundedTaskScheduler scheduler = new BoundedTaskScheduler(store, registry, ProgressEventSink.noOp(),
                new SchedulerConfig(1, 2, Duration.ofSeconds(5), Duration.ofSeconds(5), 100))) {
            scheduler.submit(task);
            assertTrue(succeeded.await(2, TimeUnit.SECONDS) == false);
            assertEquals(SchedulerTaskStatus.RETRY_WAITING, store.findById(TENANT.value(), task.id()).orElseThrow().status());
            scheduler.dispatchAvailable(1);
            assertTrue(succeeded.await(2, TimeUnit.SECONDS));
            awaitStatus(store, task.id(), SchedulerTaskStatus.SUCCEEDED);
            assertEquals(2, attempts.get());
        }
    }

    @Test
    void workerQueueIsBoundedAndProgressEventsAreSafeForSseAdapters() throws Exception {
        InMemorySchedulerTaskStore store = new InMemorySchedulerTaskStore();
        TaskHandlerRegistry registry = new TaskHandlerRegistry();
        CountDownLatch release = new CountDownLatch(1);
        List<ProgressEvent> events = new CopyOnWriteArrayList<>();
        registry.register("slow", context -> {
            context.progress(25, "started", Map.of("phase", "one"));
            release.await(2, TimeUnit.SECONDS);
        });
        try (BoundedTaskScheduler scheduler = new BoundedTaskScheduler(store, registry, events::add,
                new SchedulerConfig(1, 1, Duration.ofSeconds(5), Duration.ofSeconds(5), 100))) {
            for (int i = 0; i < 5; i++) {
                scheduler.submit(new QueuedTask("slow-" + i, TENANT, "slow", "{}", "key-" + i,
                        new RetryPolicy(1, Duration.ZERO, Duration.ZERO), Instant.now()));
            }
            assertTrue(scheduler.queueSize() <= 1);
            release.countDown();
            Thread.sleep(150);
            assertNotNull(events.stream().filter(e -> e.type() == ProgressEventType.PROGRESS).findFirst().orElse(null));
        }
    }

    private static QueuedTask task(String id, String key) {
        return new QueuedTask(id, TENANT, "noop", "{}", key,
                new RetryPolicy(1, Duration.ZERO, Duration.ZERO), Instant.now());
    }

    private static void awaitStatus(InMemorySchedulerTaskStore store, String taskId,
                                    SchedulerTaskStatus expected) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        SchedulerTaskStatus actual;
        do {
            actual = store.findById(TENANT.value(), taskId).orElseThrow().status();
            if (actual == expected) {
                return;
            }
            Thread.onSpinWait();
        } while (System.nanoTime() < deadline);
        assertEquals(expected, actual);
    }
}
