package com.reportai.application.scheduler;

import com.reportai.application.config.properties.ReportAiSchedulerProperties;
import com.reportai.common.tenant.TenantId;
import com.reportai.scheduler.BoundedTaskScheduler;
import com.reportai.scheduler.InMemorySchedulerOutboxRepository;
import com.reportai.scheduler.InMemorySchedulerTaskStore;
import com.reportai.scheduler.ProgressEventSink;
import com.reportai.scheduler.QueuedTask;
import com.reportai.scheduler.RetryPolicy;
import com.reportai.scheduler.SchedulerConfig;
import com.reportai.scheduler.TaskHandlerRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulerOutboxReconcilerTest {
    @Test
    void publishesCommittedOutboxWorkExactlyOnceToDurableScheduler() throws Exception {
        InMemorySchedulerOutboxRepository outbox = new InMemorySchedulerOutboxRepository();
        TaskHandlerRegistry handlers = new TaskHandlerRegistry();
        CountDownLatch handled = new CountDownLatch(1);
        handlers.register("report-task", context -> handled.countDown());
        BoundedTaskScheduler scheduler = new BoundedTaskScheduler(new InMemorySchedulerTaskStore(), handlers,
                ProgressEventSink.noOp(), new SchedulerConfig(1, 2, Duration.ofSeconds(5), Duration.ofSeconds(5), 100));
        ReportAiSchedulerProperties properties = new ReportAiSchedulerProperties();
        properties.setDispatchBatchSize(1);
        outbox.enqueue(new QueuedTask("task-outbox-1", TenantId.of("tenant-a"), "report-task", "{}", "key-1",
                new RetryPolicy(1, Duration.ZERO, Duration.ZERO), Instant.now()));

        try (scheduler; SchedulerOutboxReconciler reconciler = new SchedulerOutboxReconciler(outbox, scheduler, properties)) {
            assertEquals(1, reconciler.reconcileNow());
            assertEquals(0, reconciler.reconcileNow());
            assertTrue(handled.await(2, TimeUnit.SECONDS));
        }
    }
}
