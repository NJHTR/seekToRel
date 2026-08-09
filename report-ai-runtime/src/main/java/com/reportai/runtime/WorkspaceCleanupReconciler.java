package com.reportai.runtime;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * Removes dependency staging left behind by a crashed installer/executor.
 * Only the two runtime-owned names are eligible; user source and artifacts are
 * never swept. The age threshold prevents deleting a currently active task.
 */
public final class WorkspaceCleanupReconciler implements AutoCloseable {
    private static final String DEPENDENCY_DIRECTORY = ".reportai-deps";
    private static final String REQUIREMENTS_FILE = ".reportai-requirements.txt";
    private final Path root;
    private final Duration staleAfter;
    private final int maxEntries;
    private final ScheduledExecutorService executor;
    private volatile ScheduledFuture<?> future;

    public WorkspaceCleanupReconciler(Path root, Duration staleAfter, Duration interval, int maxEntries) {
        this(root, staleAfter, interval, maxEntries, Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "reportai-workspace-cleanup");
            thread.setDaemon(true);
            return thread;
        }));
    }

    WorkspaceCleanupReconciler(Path root, Duration staleAfter, Duration interval, int maxEntries,
                               ScheduledExecutorService executor) {
        this.root = Objects.requireNonNull(root, "root").toAbsolutePath().normalize();
        this.staleAfter = requirePositive(staleAfter, "staleAfter");
        Duration delay = requirePositive(interval, "interval");
        if (maxEntries <= 0 || maxEntries > 100_000) throw new IllegalArgumentException("maxEntries is invalid");
        this.maxEntries = maxEntries;
        this.executor = Objects.requireNonNull(executor, "executor");
        try {
            Files.createDirectories(this.root);
            if (Files.isSymbolicLink(this.root)) throw new IOException("workspace root is a symbolic link");
        } catch (IOException failure) {
            throw new IllegalStateException("workspace cleanup root is unavailable", failure);
        }
        this.future = executor.scheduleWithFixedDelay(this::safeSweep, delay.toMillis(), delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    /** Runs one bounded sweep synchronously; useful for startup recovery and tests. */
    public int sweep() {
        Instant cutoff = Instant.now().minus(staleAfter);
        int[] removed = {0};
        try (var paths = Files.walk(root, 3)) {
            paths.filter(path -> path.getFileName() != null
                            && (DEPENDENCY_DIRECTORY.equals(path.getFileName().toString())
                            || REQUIREMENTS_FILE.equals(path.getFileName().toString())))
                    .sorted(Comparator.reverseOrder())
                    .limit(maxEntries)
                    .forEach(path -> {
                        if (removed[0] >= maxEntries || !safeOwnedPath(path) || !isStale(path, cutoff)) return;
                        try {
                            if (DEPENDENCY_DIRECTORY.equals(path.getFileName().toString())) {
                                deleteTree(path);
                            } else {
                                Files.deleteIfExists(path);
                            }
                            removed[0]++;
                        } catch (IOException ignored) {
                            // A later sweep retries failures; never delete outside the owned names.
                        }
                    });
        } catch (IOException ignored) {
            return removed[0];
        }
        return removed[0];
    }

    private void safeSweep() {
        try { sweep(); } catch (RuntimeException ignored) { /* next interval retries */ }
    }

    private boolean safeOwnedPath(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(root) || normalized.equals(root) || Files.isSymbolicLink(normalized)) return false;
        Path workspace = normalized.getParent();
        return workspace != null && workspace.getParent() != null && workspace.getParent().equals(root)
                && !Files.isSymbolicLink(workspace);
    }

    private static boolean isStale(Path path, Instant cutoff) {
        try {
            return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toInstant().isBefore(cutoff);
        } catch (IOException failure) {
            return false;
        }
    }

    private static void deleteTree(Path target) throws IOException {
        if (Files.isSymbolicLink(target)) return;
        try (var paths = Files.walk(target)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                if (Files.isSymbolicLink(path)) {
                    Files.deleteIfExists(path);
                } else {
                    Files.deleteIfExists(path);
                }
            }
        }
    }

    private static Duration requirePositive(Duration value, String field) {
        if (value == null || value.isZero() || value.isNegative()) throw new IllegalArgumentException(field + " is invalid");
        return value;
    }

    @Override
    public void close() {
        ScheduledFuture<?> current = future;
        if (current != null) current.cancel(false);
        executor.shutdownNow();
    }
}
