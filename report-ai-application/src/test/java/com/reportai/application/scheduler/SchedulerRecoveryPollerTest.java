package com.reportai.application.scheduler;

import com.reportai.application.config.properties.ReportAiSchedulerProperties;
import com.reportai.common.tenant.TenantId;
import com.reportai.scheduler.BoundedTaskScheduler;
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

import static org.junit.jupiter.api.Assertions.assertTrue;

class SchedulerRecoveryPollerTest {
    @Test
    void pollReleasesAndDispatchesDurableQueuedWork() throws Exception {
        InMemorySchedulerTaskStore store = new InMemorySchedulerTaskStore();
        TaskHandlerRegistry handlers = new TaskHandlerRegistry();
        CountDownLatch handled = new CountDownLatch(1);
        handlers.register("report-task", context -> handled.countDown());
        store.enqueue(new QueuedTask("task-1", TenantId.of("tenant-a"), "report-task", "{}", "key-1",
                new RetryPolicy(1, Duration.ZERO, Duration.ZERO), Instant.now()));

        ReportAiSchedulerProperties properties = new ReportAiSchedulerProperties();
        properties.setDispatchBatchSize(1);
        properties.setRecoveryInterval(Duration.ofSeconds(1));
        properties.setRecoveryInitialDelay(Duration.ZERO);
        BoundedTaskScheduler scheduler = new BoundedTaskScheduler(store, handlers, ProgressEventSink.noOp(),
                new SchedulerConfig(1, 2, Duration.ofSeconds(5), Duration.ofSeconds(5), 100));
        try (scheduler; SchedulerRecoveryPoller poller = new SchedulerRecoveryPoller(scheduler, properties)) {
            assertTrue(poller.pollNow() > 0);
            assertTrue(handled.await(2, TimeUnit.SECONDS));
        }
    }
}
