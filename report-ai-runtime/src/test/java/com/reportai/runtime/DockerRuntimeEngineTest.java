package com.reportai.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DockerRuntimeEngineTest {
    @Test
    void executesAllowListedLanguageAndCleansContainer() throws Exception {
        Path root = Files.createTempDirectory("reportai-runtime-");
        Files.createDirectories(root.resolve("workspace-1"));
        FakeClient fake = new FakeClient();
        DockerRuntimeEngine engine = new DockerRuntimeEngine(fake, RuntimeConfiguration.productionDefaults(root));

        ExecutionResult result = engine.execute("workspace-1",
                new ExecutionRequest("python", "main.py", Map.of("REPORTAI_RUN", "1"),
                        new ExecutionPolicy(Duration.ofSeconds(5), 1024, 1, 64 * 1024 * 1024, false)));

        assertEquals(ExecutionResult.Status.SUCCEEDED, result.status());
        assertEquals("python:3.12-slim", fake.spec.image());
        assertTrue(fake.killed);
        assertTrue(fake.removed);
    }

    @Test
    void rejectsTraversalAndUnknownLanguageBeforeDockerCall() throws Exception {
        Path root = Files.createTempDirectory("reportai-runtime-");
        Files.createDirectories(root.resolve("workspace-1"));
        FakeClient fake = new FakeClient();
        DockerRuntimeEngine engine = new DockerRuntimeEngine(fake, RuntimeConfiguration.productionDefaults(root));

        assertEquals(ExecutionResult.Status.REJECTED, engine.execute("../outside",
                new ExecutionRequest("python", "main.py", Map.of(), ExecutionPolicy.safeDefaults())).status());
        assertEquals(ExecutionResult.Status.REJECTED, engine.execute("workspace-1",
                new ExecutionRequest("ruby", "main.rb", Map.of(), ExecutionPolicy.safeDefaults())).status());
        assertTrue(fake.spec == null);
    }

    private static final class FakeClient implements DockerSandboxClient {
        private SandboxContainerSpec spec;
        private boolean killed;
        private boolean removed;

        @Override
        public String createContainer(SandboxContainerSpec specification) {
            spec = specification;
            return "fake-container";
        }

        @Override
        public void startContainer(String containerId) {
        }

        @Override
        public SandboxExecution awaitAndCollect(String containerId, Duration timeout, long maxOutputBytes) {
            return new SandboxExecution(0, false, false, "ok", "");
        }

        @Override
        public void killContainer(String containerId) {
            killed = true;
        }

        @Override
        public void removeContainer(String containerId) {
            removed = true;
        }
    }
}
