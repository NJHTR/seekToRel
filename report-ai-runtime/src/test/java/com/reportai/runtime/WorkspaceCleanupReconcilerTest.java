package com.reportai.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkspaceCleanupReconcilerTest {
    @Test
    void sweepsOnlyStaleRuntimeOwnedFiles() throws Exception {
        Path root = Files.createTempDirectory("reportai-cleanup-");
        Path workspace = Files.createDirectories(root.resolve("task-1"));
        Path dependencies = Files.createDirectories(workspace.resolve(".reportai-deps"));
        Files.writeString(dependencies.resolve("numpy.whl"), "stale");
        Path requirements = Files.writeString(workspace.resolve(".reportai-requirements.txt"), "stale");
        Path source = Files.writeString(workspace.resolve("main.py"), "print('keep')");
        FileTime old = FileTime.from(Instant.now().minus(Duration.ofHours(2)));
        Files.setLastModifiedTime(dependencies, old);
        Files.setLastModifiedTime(requirements, old);

        var executor = Executors.newSingleThreadScheduledExecutor();
        try (WorkspaceCleanupReconciler reconciler = new WorkspaceCleanupReconciler(root,
                Duration.ofMinutes(10), Duration.ofHours(1), 10, executor)) {
            assertEquals(2, reconciler.sweep());
        }

        assertTrue(Files.exists(source));
        assertTrue(!Files.exists(dependencies));
        assertTrue(!Files.exists(requirements));
    }
}
