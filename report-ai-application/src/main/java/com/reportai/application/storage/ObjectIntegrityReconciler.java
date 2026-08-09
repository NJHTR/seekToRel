package com.reportai.application.storage;

import com.reportai.application.config.properties.ReportAiObjectStorageProperties;
import com.reportai.storage.ObjectIntegrity;
import com.reportai.storage.ObjectIntegrityReconciliationRepository;
import com.reportai.storage.ArtifactStorageResolver;
import com.reportai.storage.ObjectStorageResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded, restart-safe integrity verification for persisted object references.
 * Missing/corrupt objects stay visible in metadata and are retried; only metadata whose
 * owning aggregate no longer exists is removed automatically.
 */
public final class ObjectIntegrityReconciler implements SmartLifecycle, AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(ObjectIntegrityReconciler.class);
    private static final int BUFFER_SIZE = 8192;

    private final ObjectIntegrityReconciliationRepository repository;
    private final ObjectStorageResolver resolver;
    private final ArtifactStorageResolver artifactResolver;
    private final ReportAiObjectStorageProperties properties;
    private final ScheduledExecutorService executor;
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile ScheduledFuture<?> future;

    public ObjectIntegrityReconciler(ObjectIntegrityReconciliationRepository repository,
                                     ObjectStorageResolver resolver,
                                     ReportAiObjectStorageProperties properties) {
        this(repository, resolver, null, properties, newExecutor());
    }

    public ObjectIntegrityReconciler(ObjectIntegrityReconciliationRepository repository,
                                     ObjectStorageResolver resolver,
                                     ArtifactStorageResolver artifactResolver,
                                     ReportAiObjectStorageProperties properties) {
        this(repository, resolver, artifactResolver, properties, newExecutor());
    }

    ObjectIntegrityReconciler(ObjectIntegrityReconciliationRepository repository,
                              ObjectStorageResolver resolver,
                              ReportAiObjectStorageProperties properties,
                              ScheduledExecutorService executor) {
        this(repository, resolver, null, properties, executor);
    }

    ObjectIntegrityReconciler(ObjectIntegrityReconciliationRepository repository,
                              ObjectStorageResolver resolver,
                              ArtifactStorageResolver artifactResolver,
                              ReportAiObjectStorageProperties properties,
                              ScheduledExecutorService executor) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.resolver = Objects.requireNonNull(resolver, "resolver");
        this.artifactResolver = artifactResolver;
        this.properties = Objects.requireNonNull(properties, "properties");
        this.executor = Objects.requireNonNull(executor, "executor");
        Duration interval = properties.getReconciliationInterval();
        if (interval == null || interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("reconciliationInterval must be positive");
        }
        if (properties.getReconciliationBatchSize() < 1 || properties.getReconciliationBatchSize() > 1000) {
            throw new IllegalArgumentException("reconciliationBatchSize must be between 1 and 1000");
        }
        Duration orphanRetention = properties.getOrphanRetention();
        if (orphanRetention == null || orphanRetention.isNegative() || orphanRetention.isZero()) {
            throw new IllegalArgumentException("orphanRetention must be positive");
        }
    }

    @Override
    public void start() {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        long interval = properties.getReconciliationInterval().toMillis();
        future = executor.scheduleWithFixedDelay(this::pollSafely, interval, interval, TimeUnit.MILLISECONDS);
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

    /** Runs one bounded cycle synchronously for operations and tests. */
    public int reconcileNow() {
        int batchSize = properties.getReconciliationBatchSize();
        int orphaned = cleanupOrphanedObjects(batchSize);
        List<ObjectIntegrity> candidates = repository.findForVerification(batchSize);
        int verified = 0;
        for (ObjectIntegrity candidate : candidates) {
            try (InputStream input = open(candidate)) {
                drain(input);
                repository.markVerified(candidate.id(), Instant.now());
                verified++;
            } catch (IOException | RuntimeException failure) {
                repository.markVerificationFailed(candidate.id(), Instant.now(), safeMessage(failure));
                LOGGER.warn("object integrity verification failed for metadata {}", candidate.id(), failure);
            }
        }
        if (orphaned > 0 || verified > 0) {
            LOGGER.info("object integrity reconciliation removed {} orphan objects and verified {} objects",
                    orphaned, verified);
        }
        return verified;
    }

    private int cleanupOrphanedObjects(int batchSize) {
        Instant cutoff = Instant.now().minus(properties.getOrphanRetention());
        List<ObjectIntegrity> candidates = repository.findOrphanedMetadata(batchSize, cutoff);
        if (candidates.isEmpty()) {
            return 0;
        }
        List<String> deletedMetadataIds = new ArrayList<>(candidates.size());
        for (ObjectIntegrity candidate : candidates) {
            try {
                delete(candidate);
                deletedMetadataIds.add(candidate.id());
            } catch (RuntimeException failure) {
                LOGGER.warn("orphan object cleanup failed for metadata {}", candidate.id(), failure);
            }
        }
        if (deletedMetadataIds.isEmpty()) {
            return 0;
        }
        return repository.deleteMetadataByIds(deletedMetadataIds);
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
            reconcileNow();
        } catch (RuntimeException failure) {
            LOGGER.warn("object integrity reconciliation failed; will retry", failure);
        }
    }

    private static void drain(InputStream input) throws IOException {
        byte[] buffer = new byte[BUFFER_SIZE];
        int read;
        while ((read = input.read(buffer)) >= 0) {
            if (read == 0) {
                continue;
            }
            // The resolver performs size and SHA-256 verification as the stream is consumed.
        }
    }

    private InputStream open(ObjectIntegrity candidate) {
        if (candidate.object().objectKey().startsWith("tasks/") && artifactResolver != null) {
            return artifactResolver.open(candidate.tenantId(), candidate.object());
        }
        return resolver.open(candidate.tenantId(), candidate.object());
    }

    private void delete(ObjectIntegrity candidate) {
        if (candidate.object().objectKey().startsWith("tasks/") && artifactResolver != null) {
            artifactResolver.delete(candidate.tenantId(), candidate.object());
            return;
        }
        resolver.delete(candidate.tenantId(), candidate.object());
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
                Thread thread = new Thread(runnable, "report-ai-integrity-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }
        };
        return Executors.newSingleThreadScheduledExecutor(factory);
    }
}
