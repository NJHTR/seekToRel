package com.reportai.application.scheduler;

import com.reportai.application.config.properties.ReportAiSchedulerProperties;
import com.reportai.scheduler.BoundedTaskScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Process-local recovery loop for the durable scheduler.
 *
 * <p>The database remains the source of truth. Each tick releases expired leases and submits a
 * bounded number of worker signals. Exceptions are contained so a transient database outage does
 * not permanently cancel the periodic schedule.</p>
 */
public final class SchedulerRecoveryPoller implements SmartLifecycle, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(SchedulerRecoveryPoller.class);

    private final BoundedTaskScheduler scheduler;
    private final ReportAiSchedulerProperties properties;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile ScheduledFuture<?> future;

    public SchedulerRecoveryPoller(BoundedTaskScheduler scheduler,
                                   ReportAiSchedulerProperties properties) {
        this(scheduler, properties, newRecoveryExecutor());
    }

    SchedulerRecoveryPoller(BoundedTaskScheduler scheduler,
                            ReportAiSchedulerProperties properties,
                            ScheduledExecutorService executor) {
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.executor = Objects.requireNonNull(executor, "executor");
        validate(properties);
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        long initialDelayMillis = properties.getRecoveryInitialDelay().toMillis();
        long intervalMillis = properties.getRecoveryInterval().toMillis();
        future = executor.scheduleWithFixedDelay(this::pollSafely, initialDelayMillis, intervalMillis,
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

    /** Starts a recovery cycle synchronously; useful for readiness checks and focused tests. */
    public int pollNow() {
        return scheduler.recoverExpiredLeases(properties.getDispatchBatchSize());
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
            int signals = pollNow();
            if (signals > 0) {
                LOGGER.debug("scheduler recovery dispatched {} worker signals", signals);
            }
        } catch (RuntimeException failure) {
            // Keep the recurring task alive. The next tick can recover after a transient outage.
            LOGGER.warn("scheduler recovery poll failed; will retry", failure);
        }
    }

    private static void validate(ReportAiSchedulerProperties properties) {
        if (properties.getRecoveryInterval() == null || properties.getRecoveryInterval().isZero()
                || properties.getRecoveryInterval().isNegative()) {
            throw new IllegalArgumentException("recoveryInterval must be positive");
        }
        Duration initialDelay = properties.getRecoveryInitialDelay();
        if (initialDelay == null || initialDelay.isNegative()) {
            throw new IllegalArgumentException("recoveryInitialDelay must not be negative");
        }
        if (properties.getDispatchBatchSize() < 1) {
            throw new IllegalArgumentException("dispatchBatchSize must be positive");
        }
    }

    private static ScheduledExecutorService newRecoveryExecutor() {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger sequence = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "report-ai-scheduler-recovery-"
                        + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
        return Executors.newSingleThreadScheduledExecutor(factory);
    }
}
