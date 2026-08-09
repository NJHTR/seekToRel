package com.reportai.application.agent;

import com.reportai.agent.ConversationCompactionCandidate;
import com.reportai.agent.ConversationCompactionCandidateRepository;
import com.reportai.agent.ConversationMemorySnapshotService;
import com.reportai.agent.ConversationMemorySnapshot;
import com.reportai.application.config.properties.ReportAiMemorySnapshotProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded maintenance loop for durable conversation summaries. It is deliberately
 * independent from request threads and the report-task worker pool. PostgreSQL
 * CAS updates make concurrent application instances safe: a losing instance
 * records a retry/failure and the candidate remains eligible on the next cycle.
 */
public final class ConversationMemoryCompactionScheduler implements SmartLifecycle, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ConversationMemoryCompactionScheduler.class);

    private final ConversationCompactionCandidateRepository candidates;
    private final ConversationMemorySnapshotService snapshots;
    private final ReportAiMemorySnapshotProperties properties;
    private final ScheduledExecutorService executor;
    private final Counter candidateCounter;
    private final Counter successCounter;
    private final Counter emptyCounter;
    private final Counter retryCounter;
    private final Counter failureCounter;
    private final Counter cycleFailureCounter;
    private final Timer cycleTimer;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile ScheduledFuture<?> future;

    public ConversationMemoryCompactionScheduler(ConversationCompactionCandidateRepository candidates,
                                                  ConversationMemorySnapshotService snapshots,
                                                  ReportAiMemorySnapshotProperties properties,
                                                  MeterRegistry meterRegistry) {
        this(candidates, snapshots, properties, meterRegistry, newExecutor());
    }

    ConversationMemoryCompactionScheduler(ConversationCompactionCandidateRepository candidates,
                                           ConversationMemorySnapshotService snapshots,
                                           ReportAiMemorySnapshotProperties properties,
                                           MeterRegistry meterRegistry,
                                           ScheduledExecutorService executor) {
        this.candidates = Objects.requireNonNull(candidates, "candidates");
        this.snapshots = Objects.requireNonNull(snapshots, "snapshots");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.executor = Objects.requireNonNull(executor, "executor");
        Objects.requireNonNull(meterRegistry, "meterRegistry");
        validate(properties);
        this.candidateCounter = Counter.builder("report_ai.memory_snapshot.compaction.candidates")
                .description("Conversation histories selected for memory snapshot compaction")
                .register(meterRegistry);
        this.successCounter = Counter.builder("report_ai.memory_snapshot.compaction.success")
                .description("Successful durable memory snapshot compactions")
                .register(meterRegistry);
        this.emptyCounter = Counter.builder("report_ai.memory_snapshot.compaction.empty")
                .description("Candidates skipped because the conversation disappeared or has no messages")
                .register(meterRegistry);
        this.retryCounter = Counter.builder("report_ai.memory_snapshot.compaction.retry")
                .description("Additional compaction attempts after a failure")
                .register(meterRegistry);
        this.failureCounter = Counter.builder("report_ai.memory_snapshot.compaction.failure")
                .description("Candidates that exhausted their compaction attempts")
                .register(meterRegistry);
        this.cycleFailureCounter = Counter.builder("report_ai.memory_snapshot.compaction.cycle_failure")
                .description("Compaction cycles that failed before candidate processing")
                .register(meterRegistry);
        this.cycleTimer = Timer.builder("report_ai.memory_snapshot.compaction.cycle_duration")
                .description("Duration of one bounded memory snapshot compaction cycle")
                .register(meterRegistry);
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        long intervalMillis = durationMillis(properties.getInterval());
        long initialDelayMillis = durationMillis(properties.getInitialDelay());
        future = executor.scheduleWithFixedDelay(this::pollSafely, initialDelayMillis,
                intervalMillis, TimeUnit.MILLISECONDS);
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

    /** Executes one bounded cycle synchronously; useful for operations and tests. */
    public CompactionCycleResult compactNow() {
        Timer.Sample timer = Timer.start();
        int selected = 0;
        int succeeded = 0;
        int empty = 0;
        int retries = 0;
        int failures = 0;
        try {
            List<ConversationCompactionCandidate> queried = candidates.findCandidates(properties.getBatchSize());
            if (queried == null) {
                throw new IllegalStateException("conversation compaction candidate query returned null");
            }
            List<ConversationCompactionCandidate> batch = queried.size() <= properties.getBatchSize()
                    ? queried : List.copyOf(queried.subList(0, properties.getBatchSize()));
            selected = batch.size();
            candidateCounter.increment(selected);
            for (ConversationCompactionCandidate candidate : batch) {
                boolean completed = false;
                for (int attempt = 1; attempt <= properties.getMaxAttempts(); attempt++) {
                    try {
                        Optional<ConversationMemorySnapshot> result = snapshots.compactNow(
                                candidate.tenantId(), candidate.conversationId(),
                                properties.getMaxSummaryChars());
                        if (result.isPresent()) {
                            succeeded++;
                            successCounter.increment();
                        } else {
                            empty++;
                            emptyCounter.increment();
                        }
                        completed = true;
                        break;
                    } catch (RuntimeException failure) {
                        if (attempt < properties.getMaxAttempts()) {
                            retries++;
                            retryCounter.increment();
                            continue;
                        }
                        failures++;
                        failureCounter.increment();
                        LOGGER.warn("conversation memory compaction failed after {} attempts for candidate {}",
                                attempt, candidate.conversationId(), failure);
                    }
                }
                if (!completed) {
                    // The candidate remains eligible and will be retried in a later cycle.
                    continue;
                }
            }
            return new CompactionCycleResult(selected, succeeded, empty, retries, failures);
        } finally {
            timer.stop(cycleTimer);
        }
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
        if (!running.get()) {
            return;
        }
        try {
            CompactionCycleResult result = compactNow();
            if (result.selected() > 0) {
                LOGGER.info("conversation memory compaction selected {}, succeeded {}, empty {}, retries {}, failures {}",
                        result.selected(), result.succeeded(), result.empty(), result.retries(), result.failures());
            }
        } catch (RuntimeException failure) {
            cycleFailureCounter.increment();
            LOGGER.warn("conversation memory compaction cycle failed; candidates will be retried", failure);
        }
    }

    private static void validate(ReportAiMemorySnapshotProperties properties) {
        if (properties.getInterval() == null || properties.getInterval().isZero()
                || properties.getInterval().isNegative()) {
            throw new IllegalArgumentException("memory snapshot interval must be positive");
        }
        if (properties.getInitialDelay() == null || properties.getInitialDelay().isNegative()) {
            throw new IllegalArgumentException("memory snapshot initial delay must not be negative");
        }
        if (properties.getBatchSize() < 1 || properties.getBatchSize() > 1_000) {
            throw new IllegalArgumentException("memory snapshot batch size must be between 1 and 1000");
        }
        if (properties.getMaxSummaryChars() < 128 || properties.getMaxSummaryChars() > 128 * 1024) {
            throw new IllegalArgumentException("memory snapshot summary size is outside the permitted range");
        }
        if (properties.getMaxAttempts() < 1 || properties.getMaxAttempts() > 10) {
            throw new IllegalArgumentException("memory snapshot max attempts must be between 1 and 10");
        }
    }

    private static long durationMillis(Duration duration) {
        long millis = duration.toMillis();
        return millis > 0 ? millis : 1;
    }

    private static ScheduledExecutorService newExecutor() {
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger sequence = new AtomicInteger();

            @Override
            public Thread newThread(Runnable runnable) {
                Thread thread = new Thread(runnable, "report-ai-memory-snapshot-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
        return Executors.newSingleThreadScheduledExecutor(factory);
    }

    public record CompactionCycleResult(int selected, int succeeded, int empty, int retries, int failures) {
        public CompactionCycleResult {
            if (selected < 0 || succeeded < 0 || empty < 0 || retries < 0 || failures < 0) {
                throw new IllegalArgumentException("compaction result counts must not be negative");
            }
        }
    }
}
