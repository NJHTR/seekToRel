package com.reportai.application.scheduler;

import com.reportai.application.config.properties.ReportAiSchedulerProperties;
import com.reportai.scheduler.BoundedTaskScheduler;
import com.reportai.scheduler.SchedulerOutboxEntry;
import com.reportai.scheduler.SchedulerOutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Publishes committed scheduler outbox rows after the request transaction has completed.
 * Leases make the poller safe when several application instances are running.
 */
public final class SchedulerOutboxReconciler implements SmartLifecycle, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerOutboxReconciler.class);

    private final SchedulerOutboxRepository outbox;
    private final BoundedTaskScheduler scheduler;
    private final ReportAiSchedulerProperties properties;
    private final String owner = "outbox-" + UUID.randomUUID();
    private final ScheduledExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile ScheduledFuture<?> future;

    public SchedulerOutboxReconciler(SchedulerOutboxRepository outbox,
                                     BoundedTaskScheduler scheduler,
                                     ReportAiSchedulerProperties properties) {
        this(outbox, scheduler, properties, newExecutor());
    }

    SchedulerOutboxReconciler(SchedulerOutboxRepository outbox,
                              BoundedTaskScheduler scheduler,
                              ReportAiSchedulerProperties properties,
                              ScheduledExecutorService executor) {
        this.outbox = Objects.requireNonNull(outbox, "outbox");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.executor = Objects.requireNonNull(executor, "executor");
        if (properties.getRecoveryInterval() == null || properties.getRecoveryInterval().isNegative()
                || properties.getRecoveryInterval().isZero()) {
            throw new IllegalArgumentException("recoveryInterval must be positive");
        }
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        Duration interval = properties.getRecoveryInterval();
        future = executor.scheduleWithFixedDelay(this::pollSafely, 0, interval.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    @Override
    public void stop() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        ScheduledFuture<?> current = future;
        if (current != null) {
            current.cancel(false);
        }
        future = null;
    }

    @Override
    public boolean isRunning() {
        return running.get();
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    /** Performs one bounded reconciliation cycle synchronously. */
    public int reconcileNow() {
        Instant now = Instant.now();
        int purged = outbox.purgePublished(now.minus(properties.getOutboxPublishedRetention()),
                properties.getOutboxCleanupBatchSize());
        List<SchedulerOutboxEntry> entries = outbox.claimReady(now, owner,
                properties.getLeaseDuration(), properties.getDispatchBatchSize());
        int published = 0;
        for (SchedulerOutboxEntry entry : entries) {
            try {
                scheduler.submit(entry.task());
                if (outbox.markPublished(entry.id(), owner, Instant.now())) {
                    published++;
                }
            } catch (RuntimeException failure) {
                Duration delay = entry.task().retryPolicy().backoffForAttempt(Math.max(2, entry.attempts() + 1));
                outbox.reschedule(entry.id(), owner, Instant.now(), Instant.now().plus(delay), safeMessage(failure));
                LOGGER.warn("scheduler outbox publication failed for {}", entry.id(), failure);
            }
        }
        if (purged > 0) {
            LOGGER.debug("purged {} published scheduler outbox messages", purged);
        }
        return published;
    }

    @Override
    public void close() {
        stop();
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void pollSafely() {
        try {
            int published = reconcileNow();
            if (published > 0) {
                LOGGER.debug("published {} scheduler outbox messages", published);
            }
        } catch (RuntimeException failure) {
            LOGGER.warn("scheduler outbox reconciliation failed; will retry", failure);
        }
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static ScheduledExecutorService newExecutor() {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger sequence = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "report-ai-outbox-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
        return Executors.newSingleThreadScheduledExecutor(factory);
    }
}
