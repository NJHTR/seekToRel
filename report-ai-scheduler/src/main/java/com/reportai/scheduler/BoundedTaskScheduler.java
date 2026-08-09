package com.reportai.scheduler;

import com.reportai.common.tenant.TenantId;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Durable task dispatcher backed by a strictly bounded worker pool. Recovery is driven by
 * dispatchAvailable(), so a database-backed adapter can resume work after process restart.
 */
public final class BoundedTaskScheduler implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(BoundedTaskScheduler.class);
    private static final Pattern EXPLICIT_ERROR_CODE = Pattern.compile(
            "(?<![A-Z0-9_])(?:ENV|LLM|PYTHON|DOCX|TASK)_[A-Z0-9_]{2,60}(?![A-Z0-9_])");
    private final SchedulerTaskStore store;
    private final TaskHandlerRegistry handlers;
    private final ProgressEventSink events;
    private final SchedulerConfig config;
    private final String schedulerId = "scheduler-" + UUID.randomUUID();
    private final ThreadPoolExecutor executor;
    private final ScheduledExecutorService leaseHeartbeat;

    public BoundedTaskScheduler(SchedulerTaskStore store, TaskHandlerRegistry handlers,
                                ProgressEventSink events, SchedulerConfig config) {
        this.store = require(store, "store");
        this.handlers = require(handlers, "handlers");
        this.events = events == null ? ProgressEventSink.noOp() : events;
        this.config = config == null ? SchedulerConfig.defaults() : config;
        ThreadFactory factory = new NamedThreadFactory(schedulerId);
        this.executor = new ThreadPoolExecutor(this.config.workerCount(), this.config.workerCount(),
                0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(this.config.queueCapacity()),
                factory, new ThreadPoolExecutor.AbortPolicy());
        this.leaseHeartbeat = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, schedulerId + "-lease-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
        this.executor.prestartAllCoreThreads();
    }

    public SubmissionResult submit(QueuedTask task) {
        SubmissionResult result = store.enqueue(task);
        if (result.created()) {
            publish(task.tenantId(), task.id(), 0, ProgressEventType.QUEUED, 0, "task queued", Map.of());
            dispatchAvailable(1);
        }
        return result;
    }

    /** Enqueues at most maxSignals bounded worker signals. Safe to call from a poller. */
    public int dispatchAvailable(int maxSignals) {
        if (maxSignals < 1) {
            throw new IllegalArgumentException("maxSignals must be positive");
        }
        int submitted = 0;
        for (int i = 0; i < maxSignals; i++) {
            try {
                executor.execute(this::claimAndRunOne);
                submitted++;
            } catch (RejectedExecutionException rejected) {
                break;
            }
        }
        return submitted;
    }

    /** Requeues expired leases in the durable store, then fills available worker signals. */
    public int recoverExpiredLeases(int maxSignals) {
        store.releaseExpiredLeases(Instant.now());
        return dispatchAvailable(maxSignals);
    }

    public boolean cancel(String tenantId, String taskId) {
        boolean cancelled = store.cancel(taskId, tenantId, Instant.now());
        if (cancelled) {
            publish(TenantId.of(tenantId), taskId, 0, ProgressEventType.CANCELLED, 0, "task cancelled", Map.of());
        }
        return cancelled;
    }

    public int queueSize() {
        return executor.getQueue().size();
    }

    public int activeCount() {
        return executor.getActiveCount();
    }

    private void claimAndRunOne() {
        String workerId = schedulerId + ":" + Thread.currentThread().getName();
        Optional<ScheduledTask> optional = store.claimNext(Instant.now(), workerId, config.leaseDuration());
        if (optional.isEmpty()) {
            return;
        }
        ScheduledTask task = optional.get();
        publish(task.tenantId(), task.id(), task.attempt(), ProgressEventType.STARTED, 0, "task started", Map.of());
        LOGGER.info("task started taskId={} type={} attempt={}", task.id(), task.type(), task.attempt());
        DeadlineContext context = new DeadlineContext(task, workerId, Instant.now().plus(config.executionTimeout()));
        long heartbeatMillis = Math.max(50L, config.leaseDuration().toMillis() / 3L);
        ScheduledFuture<?> heartbeat = leaseHeartbeat.scheduleAtFixedRate(context::renewLease,
                heartbeatMillis, heartbeatMillis, TimeUnit.MILLISECONDS);
        try {
            handlers.require(task.type()).handle(context);
            if (context.timedOut()) {
                throw new TaskTimeoutException("task execution deadline exceeded");
            }
            if (context.markSucceeded()) {
                publish(task.tenantId(), task.id(), task.attempt(), ProgressEventType.SUCCEEDED, 100,
                        "task completed", Map.of());
                LOGGER.info("task completed taskId={} type={} attempt={}", task.id(), task.type(), task.attempt());
            }
        } catch (Exception failure) {
            String message = safeMessage(failure);
            String errorCode = safeErrorCode(failure);
            boolean retryable = !(failure instanceof NonRetryableTaskException);
            FailureTransition transition = context.markFailure(retryable, message);
            LOGGER.error("task failed taskId={} type={} attempt={} retryable={} terminalStatus={} errorCode={}",
                    task.id(), task.type(), task.attempt(), retryable, transition.status(), errorCode);
            if (transition.retryScheduled()) {
                publish(task.tenantId(), task.id(), task.attempt(), ProgressEventType.RETRY_SCHEDULED, 0,
                        "task scheduled for retry", Map.of("errorCode", errorCode, "retryable", "true"));
            } else if (transition.status() == SchedulerTaskStatus.FAILED) {
                publish(task.tenantId(), task.id(), task.attempt(), ProgressEventType.FAILED, 0, "task failed",
                        Map.of("errorCode", errorCode, "retryable", "false"));
            }
        } finally {
            heartbeat.cancel(false);
        }
    }

    private void publish(TenantId tenantId, String taskId, int attempt, ProgressEventType type, int percent,
                         String message, Map<String, String> attributes) {
        String safe = message == null ? "" : message;
        if (safe.length() > config.maxProgressMessageLength()) {
            safe = safe.substring(0, config.maxProgressMessageLength());
        }
        events.publish(tenantId, new ProgressEvent(UUID.randomUUID().toString(), taskId, attempt, type,
                percent, safe, Instant.now(), attributes));
    }

    private static String safeMessage(Throwable throwable) {
        String message = throwable.getMessage();
        if (message == null || message.isBlank()) {
            return "task execution failed";
        }
        return ProgressEvent.sanitizeMessage(message);
    }

    private static String safeErrorCode(Throwable throwable) {
        // Domain handlers may wrap a typed failure in a retryable boundary so
        // that the scheduler can decide whether to replay it. Preserve an
        // explicit domain code from either the wrapper or its cause; otherwise
        // the UI would turn a useful PYTHON_* or LLM_* diagnosis into the
        // misleading generic TASK_EXECUTION_FAILED.
        String explicit = explicitErrorCode(throwable);
        if (explicit != null) return explicit;
        if (throwable instanceof NonRetryableTaskException) return "TASK_NOT_RETRYABLE";
        if (throwable instanceof TaskTimeoutException) {
            return "TASK_TIMEOUT";
        }
        return "TASK_EXECUTION_FAILED";
    }

    private static String explicitErrorCode(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            String message = current.getMessage();
            if (message == null || message.isBlank()) continue;
            Matcher matcher = EXPLICIT_ERROR_CODE.matcher(message);
            if (matcher.find()) return matcher.group();
        }
        return null;
    }

    private static <T> T require(T value, String name) {
        if (value == null) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }

    @Override
    public void close() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException interrupted) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            leaseHeartbeat.shutdownNow();
        }
    }

    private final class DeadlineContext implements TaskExecutionContext {
        private final ScheduledTask task;
        private final String workerId;
        private final Instant deadline;
        private volatile long currentVersion;

        private DeadlineContext(ScheduledTask task, String workerId, Instant deadline) {
            this.task = task;
            this.workerId = workerId;
            this.deadline = deadline;
            this.currentVersion = task.version();
        }

        @Override public String taskId() { return task.id(); }
        @Override public String type() { return task.type(); }
        @Override public String payload() { return task.payload(); }
        @Override public int attempt() { return task.attempt(); }
        @Override public Instant deadline() { return deadline; }
        @Override public boolean timedOut() { return Instant.now().isAfter(deadline); }
        private synchronized boolean markSucceeded() {
            return store.markSucceeded(task.id(), workerId, currentVersion, Instant.now());
        }
        private synchronized FailureTransition markFailure(boolean retryable, String message) {
            return store.markFailure(task.id(), workerId, currentVersion, Instant.now(), retryable, message);
        }
        @Override public synchronized boolean renewLease() {
            if (timedOut()) {
                return false;
            }
            boolean renewed = store.renewLease(task.id(), workerId, currentVersion, Instant.now(),
                    config.leaseDuration());
            if (renewed) {
                currentVersion++;
            }
            return renewed;
        }
        @Override public void progress(int percent, String message, Map<String, String> attributes) {
            if (timedOut()) {
                throw new TaskTimeoutException("task execution deadline exceeded");
            }
            publish(task.tenantId(), task.id(), task.attempt(), ProgressEventType.PROGRESS, percent, message, attributes);
        }
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private final String prefix;
        private final AtomicInteger sequence = new AtomicInteger();
        private NamedThreadFactory(String prefix) { this.prefix = prefix; }
        @Override public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, prefix + "-worker-" + sequence.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        }
    }

    private static final class TaskTimeoutException extends RuntimeException {
        private TaskTimeoutException(String message) { super(message); }
    }
}
