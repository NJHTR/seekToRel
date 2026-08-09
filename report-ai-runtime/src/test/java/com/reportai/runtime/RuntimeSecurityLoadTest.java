package com.reportai.runtime;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Security and bounded-concurrency tests for the runtime policy boundary. */
class RuntimeSecurityLoadTest {
    @Test
    void rejectsCommandInjectionAndDangerousEnvironmentBeforeDocker() throws IOException {
        Path root = Files.createTempDirectory("reportai-runtime-security-");
        Files.createDirectories(root.resolve("workspace-1"));
        RecordingClient client = new RecordingClient();
        DockerRuntimeEngine engine = new DockerRuntimeEngine(client, RuntimeConfiguration.productionDefaults(root));

        ExecutionResult commandInjection = engine.execute("workspace-1",
                new ExecutionRequest("java", "Main.java;touch-pwned.java", Map.of(), ExecutionPolicy.safeDefaults()));
        ExecutionResult pathOverride = engine.execute("workspace-1",
                new ExecutionRequest("java", "Main.java", Map.of("PATH", "/workspace"),
                        ExecutionPolicy.safeDefaults()));
        ExecutionResult loaderOverride = engine.execute("workspace-1",
                new ExecutionRequest("java", "Main.java", Map.of("JAVA_TOOL_OPTIONS", "-javaagent:/tmp/a"),
                        ExecutionPolicy.safeDefaults()));
        ExecutionResult networkRequest = engine.execute("workspace-1",
                new ExecutionRequest("java", "Main.java", Map.of(),
                        new ExecutionPolicy(Duration.ofSeconds(5), 1_024, 1, 64L * 1024 * 1024, true)));
        ExecutionResult credentialEnvironment = engine.execute("workspace-1",
                new ExecutionRequest("java", "Main.java", Map.of("REPORTAI_API_KEY", "should-not-be-forwarded"),
                        ExecutionPolicy.safeDefaults()));

        assertEquals(ExecutionResult.Status.REJECTED, commandInjection.status());
        assertEquals(ExecutionResult.Status.REJECTED, pathOverride.status());
        assertEquals(ExecutionResult.Status.REJECTED, loaderOverride.status());
        assertEquals(ExecutionResult.Status.REJECTED, networkRequest.status());
        assertEquals(ExecutionResult.Status.REJECTED, credentialEnvironment.status());
        assertEquals(0, client.created.get());
    }

    @Test
    void allowsNonSensitiveRuntimeInputs() throws IOException {
        Path root = Files.createTempDirectory("reportai-runtime-env-");
        Files.createDirectories(root.resolve("workspace-1"));
        RecordingClient client = new RecordingClient();
        DockerRuntimeEngine engine = new DockerRuntimeEngine(client, RuntimeConfiguration.productionDefaults(root));

        ExecutionResult result = engine.execute("workspace-1",
                new ExecutionRequest("python", "main.py", Map.of("REPORTAI_TASK_ID", "task-1"),
                        ExecutionPolicy.safeDefaults()));

        assertEquals(ExecutionResult.Status.SUCCEEDED, result.status());
        assertEquals("task-1", client.lastSpecification.environment().get("REPORTAI_TASK_ID"));
    }

    @Test
    void rejectsRequestsThatExceedPlatformLimits() throws IOException {
        Path root = Files.createTempDirectory("reportai-runtime-limits-");
        Files.createDirectories(root.resolve("workspace-1"));
        RecordingClient client = new RecordingClient();
        RuntimeConfiguration configuration = RuntimeConfiguration.productionDefaults(root);
        DockerRuntimeEngine engine = new DockerRuntimeEngine(client, configuration);

        assertEquals(ExecutionResult.Status.REJECTED, engine.execute("workspace-1",
                new ExecutionRequest("java", "Main.java", Map.of(),
                        new ExecutionPolicy(configuration.maxTimeout().plusMillis(1), 1, 1, 1, false))).status());
        assertEquals(ExecutionResult.Status.REJECTED, engine.execute("workspace-1",
                new ExecutionRequest("java", "Main.java", Map.of(),
                        new ExecutionPolicy(Duration.ofSeconds(1), configuration.maxOutputBytes() + 1,
                                1, 1, false))).status());
        assertEquals(ExecutionResult.Status.REJECTED, engine.execute("workspace-1",
                new ExecutionRequest("java", "Main.java", Map.of(),
                        new ExecutionPolicy(Duration.ofSeconds(1), 1, configuration.maxCpuLimit() + 1,
                                1, false))).status());
        assertEquals(0, client.created.get());
    }

    @Test
    void rejectsSymlinkedWorkspaceAndWorkspaceFiles() throws IOException {
        Path root = Files.createTempDirectory("reportai-runtime-links-");
        Path outside = Files.createTempDirectory("reportai-runtime-outside-");
        Path linkedWorkspace = root.resolve("linked-workspace");
        boolean linksSupported = tryCreateSymbolicLink(linkedWorkspace, outside);
        Assumptions.assumeTrue(linksSupported, "symbolic links are not available on this host");

        Path workspace = root.resolve("workspace-1");
        Files.createDirectories(workspace);
        Path outsideFile = outside.resolve("outside.txt");
        Files.writeString(outsideFile, "secret");
        Path linkedFile = workspace.resolve("linked.txt");
        assertTrue(tryCreateSymbolicLink(linkedFile, outsideFile));

        RecordingClient client = new RecordingClient();
        DockerRuntimeEngine engine = new DockerRuntimeEngine(client, RuntimeConfiguration.productionDefaults(root));
        assertEquals(ExecutionResult.Status.REJECTED, engine.execute("linked-workspace",
                new ExecutionRequest("java", "Main.java", Map.of(), ExecutionPolicy.safeDefaults())).status());
        assertEquals(ExecutionResult.Status.REJECTED, engine.execute("workspace-1",
                new ExecutionRequest("java", "Main.java", Map.of(), ExecutionPolicy.safeDefaults())).status());
        assertEquals(0, client.created.get());
    }

    @Test
    void supportsConcurrentExecutionsWithoutCrossRequestState() throws Exception {
        Path root = Files.createTempDirectory("reportai-runtime-load-");
        int requests = 32;
        for (int index = 0; index < requests; index++) {
            Files.createDirectories(root.resolve("workspace-" + index));
        }
        RecordingClient client = new RecordingClient();
        DockerRuntimeEngine engine = new DockerRuntimeEngine(client, RuntimeConfiguration.productionDefaults(root));
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Future<ExecutionResult>> futures = new ArrayList<>(requests);
            for (int index = 0; index < requests; index++) {
                String workspace = "workspace-" + index;
                futures.add(executor.submit(() -> engine.execute(workspace,
                        new ExecutionRequest("java", "Main.java", Map.of("REPORTAI_TASK", workspace),
                                new ExecutionPolicy(Duration.ofSeconds(5), 1_024, 1,
                                        64L * 1024 * 1024, false)))));
            }
            for (Future<ExecutionResult> future : futures) {
                assertEquals(ExecutionResult.Status.SUCCEEDED, future.get(10, TimeUnit.SECONDS).status());
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
        assertEquals(requests, client.created.get());
        assertEquals(requests, client.killed.get());
        assertEquals(requests, client.removed.get());
    }

    private static boolean tryCreateSymbolicLink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (UnsupportedOperationException | SecurityException | IOException ignored) {
            return false;
        }
    }

    private static final class RecordingClient implements DockerSandboxClient {
        private final AtomicInteger created = new AtomicInteger();
        private final AtomicInteger killed = new AtomicInteger();
        private final AtomicInteger removed = new AtomicInteger();
        private final ConcurrentMap<String, SandboxContainerSpec> specifications = new ConcurrentHashMap<>();
        private volatile SandboxContainerSpec lastSpecification;

        @Override
        public String createContainer(SandboxContainerSpec specification) {
            String id = "container-" + UUID.randomUUID();
            specifications.put(id, specification);
            lastSpecification = specification;
            created.incrementAndGet();
            return id;
        }

        @Override
        public void startContainer(String containerId) {
            assertTrue(specifications.containsKey(containerId));
        }

        @Override
        public SandboxExecution awaitAndCollect(String containerId, Duration timeout, long maxOutputBytes) {
            assertTrue(specifications.containsKey(containerId));
            return new SandboxExecution(0, false, false, "ok", "");
        }

        @Override
        public void killContainer(String containerId) {
            killed.incrementAndGet();
        }

        @Override
        public void removeContainer(String containerId) {
            specifications.remove(containerId);
            removed.incrementAndGet();
        }
    }
}
