package com.reportai.runtime;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        assertEquals("python@sha256:57cd7c3a7a273101a6485ba99423ee568157882804b1124b4dd04266317710de",
                fake.spec.image());
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

    @Test
    void rejectsMutableRuntimeImageAtConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> new LanguageRuntime("python", "python:3.12", entry -> List.of("python", entry)));
    }

    @Test
    void rejectsUnmanagedNetworkAtContainerBoundary() {
        assertThrows(IllegalArgumentException.class, () -> new SandboxContainerSpec(
                "python@sha256:57cd7c3a7a273101a6485ba99423ee568157882804b1124b4dd04266317710de",
                List.of("python", "main.py"), Map.of(), Path.of("workspace"), "/workspace",
                1, 64L * 1024 * 1024, true));
    }

    @Test
    void signedCatalogImageReplacesOnlyTheAllowListedLanguageImage() {
        RuntimeConfiguration base = RuntimeConfiguration.productionDefaults(Path.of("."));
        String scientificImage = "registry.example/reportai-python-scientific@sha256:" + "1".repeat(64);
        EnvironmentCatalog catalog = EnvironmentCatalog.of(
                List.of(new RuntimeImageProfile("python", scientificImage, "scientific")), List.of());

        RuntimeConfiguration resolved = base.withCatalog(catalog);

        assertEquals(scientificImage, resolved.language("python").image());
        assertEquals(base.language("java").image(), resolved.language("java").image());
    }

    @Test
    void installsApprovedDependenciesFromOfflineWheelhouseAndCleansEphemeralTarget() throws Exception {
        Path root = Files.createTempDirectory("reportai-runtime-");
        Files.createDirectories(root.resolve("workspace-1"));
        Files.createDirectories(root.resolve("workspace-1/.reportai-deps"));
        Files.writeString(root.resolve("workspace-1/.reportai-deps/stale.whl"), "stale");
        FakeClient fake = new FakeClient();
        DockerRuntimeEngine engine = new DockerRuntimeEngine(fake, RuntimeConfiguration.productionDefaults(root));

        ExecutionResult result = engine.execute("workspace-1", new ExecutionRequest("python", "main.py", Map.of(),
                ExecutionPolicy.safeDefaults(), List.of("numpy==1.26.4")));

        assertEquals(ExecutionResult.Status.SUCCEEDED, result.status());
        assertEquals(2, fake.specs.size());
        assertTrue(fake.specs.get(0).ephemeralDependencies());
        assertTrue(fake.specs.get(0).dependencyVolumeWritable());
        assertTrue(!fake.specs.get(1).dependencyVolumeWritable());
        String command = String.join(" ", fake.specs.get(0).command());
        assertTrue(command.contains("--no-index"));
        assertTrue(command.contains("--find-links=/opt/reportai/wheels"));
        assertTrue(command.contains("--target /tmp/reportai-deps"));
        assertTrue(String.join(" ", fake.specs.get(1).command()).contains("PYTHONPATH='/home/reportai/.reportai-deps'"));
        assertTrue(!Files.exists(root.resolve("workspace-1/.reportai-deps")));
    }

    @Test
    void rejectsUnapprovedDependencyBeforeDockerCall() throws Exception {
        Path root = Files.createTempDirectory("reportai-runtime-");
        Files.createDirectories(root.resolve("workspace-1"));
        FakeClient fake = new FakeClient();
        DockerRuntimeEngine engine = new DockerRuntimeEngine(fake, RuntimeConfiguration.productionDefaults(root));

        ExecutionResult result = engine.execute("workspace-1", new ExecutionRequest("python", "main.py", Map.of(),
                ExecutionPolicy.safeDefaults(), List.of("requests==2.32.3")));

        assertEquals(ExecutionResult.Status.REJECTED, result.status());
        assertTrue(fake.spec == null);
    }

    @Test
    void rejectsUnpinnedDependencyForReproducibility() throws Exception {
        Path root = Files.createTempDirectory("reportai-runtime-");
        Files.createDirectories(root.resolve("workspace-1"));
        FakeClient fake = new FakeClient();
        DockerRuntimeEngine engine = new DockerRuntimeEngine(fake, RuntimeConfiguration.productionDefaults(root));

        ExecutionResult result = engine.execute("workspace-1", new ExecutionRequest("python", "main.py", Map.of(),
                ExecutionPolicy.safeDefaults(), List.of("numpy")));

        assertEquals(ExecutionResult.Status.REJECTED, result.status());
        assertTrue(fake.spec == null);
    }

    @Test
    void resolvedDependenciesUseHashLockedRequirementsAndCleanup() throws Exception {
        Path root = Files.createTempDirectory("reportai-runtime-");
        Files.createDirectories(root.resolve("workspace-1"));
        FakeClient fake = new FakeClient();
        DockerRuntimeEngine engine = new DockerRuntimeEngine(fake, RuntimeConfiguration.productionDefaults(root));
        String image = "python@sha256:57cd7c3a7a273101a6485ba99423ee568157882804b1124b4dd04266317710de";
        EnvironmentManifest manifest = new EnvironmentManifest(1, "python", "main.py",
                List.of(new DependencySpec("numpy", "1.26.4", "0".repeat(64))), List.of(), image, "scientific");

        ExecutionResult result = engine.execute("workspace-1", new ExecutionRequest("python", "main.py", Map.of(),
                ExecutionPolicy.safeDefaults(), PythonDependencyRequest.fromManifest(manifest)));

        assertEquals(ExecutionResult.Status.SUCCEEDED, result.status());
        assertEquals(2, fake.specs.size());
        String command = String.join(" ", fake.specs.get(0).command());
        assertTrue(command.contains("--require-hashes"));
        assertTrue(command.contains("--hash=sha256:" + "0".repeat(64)));
        assertTrue(command.contains("/workspace/.reportai-requirements.txt"));
    }

    @Test
    void catalogTransitiveDependenciesAreAcceptedByRuntimePolicy() throws Exception {
        Path root = Files.createTempDirectory("reportai-runtime-");
        Files.createDirectories(root.resolve("workspace-1"));
        FakeClient fake = new FakeClient();
        String image = "python@sha256:57cd7c3a7a273101a6485ba99423ee568157882804b1124b4dd04266317710de";
        EnvironmentCatalog catalog = EnvironmentCatalog.withDependencyGraph(
                List.of(new RuntimeImageProfile("python", image, "scientific")),
                List.of(new CatalogDependency(new DependencySpec("numpy", "1.26.4", "a".repeat(64)),
                        List.of(new DependencyCoordinate("packaging", "26.3"))),
                        new CatalogDependency(new DependencySpec("packaging", "26.3", "b".repeat(64)))));
        RuntimeConfiguration configuration = RuntimeConfiguration.productionDefaults(root).withCatalog(catalog);
        DockerRuntimeEngine engine = new DockerRuntimeEngine(fake, configuration);
        EnvironmentManifest manifest = new EnvironmentResolver(catalog).resolve("python", "main.py",
                new PythonDependencyRequest(List.of("numpy==1.26.4")));

        ExecutionResult result = engine.execute("workspace-1", new ExecutionRequest("python", "main.py", Map.of(),
                ExecutionPolicy.safeDefaults(), PythonDependencyRequest.fromManifest(manifest)));

        assertEquals(ExecutionResult.Status.SUCCEEDED, result.status());
        assertTrue(String.join(" ", fake.specs.get(0).command()).contains("packaging==26.3"));
    }

    @Test
    void signedCatalogRejectsUnresolvedExactVersionRequestsAtRuntimeBoundary() throws Exception {
        Path root = Files.createTempDirectory("reportai-runtime-catalog-lock-");
        Files.createDirectories(root.resolve("workspace-1"));
        FakeClient fake = new FakeClient();
        String image = "python@sha256:57cd7c3a7a273101a6485ba99423ee568157882804b1124b4dd04266317710de";
        DependencySpec approved = new DependencySpec("numpy", "1.26.4", "a".repeat(64));
        EnvironmentCatalog catalog = EnvironmentCatalog.of(
                List.of(new RuntimeImageProfile("python", image, "scientific")), List.of(approved));
        DockerRuntimeEngine engine = new DockerRuntimeEngine(fake,
                RuntimeConfiguration.productionDefaults(root).withCatalog(catalog));

        ExecutionResult result = engine.execute("workspace-1", new ExecutionRequest("python", "main.py", Map.of(),
                ExecutionPolicy.safeDefaults(), List.of("numpy==1.26.4")));

        assertEquals(ExecutionResult.Status.REJECTED, result.status());
        assertTrue(fake.spec == null);
    }

    @Test
    void signedCatalogRejectsHashMismatchEvenWhenCoordinateIsApproved() throws Exception {
        Path root = Files.createTempDirectory("reportai-runtime-catalog-hash-");
        Files.createDirectories(root.resolve("workspace-1"));
        FakeClient fake = new FakeClient();
        String image = "python@sha256:57cd7c3a7a273101a6485ba99423ee568157882804b1124b4dd04266317710de";
        EnvironmentCatalog catalog = EnvironmentCatalog.of(
                List.of(new RuntimeImageProfile("python", image, "scientific")),
                List.of(new DependencySpec("numpy", "1.26.4", "a".repeat(64))));
        EnvironmentManifest forged = new EnvironmentManifest(1, "python", "main.py",
                List.of(new DependencySpec("numpy", "1.26.4", "b".repeat(64))), List.of(), image, "scientific");
        DockerRuntimeEngine engine = new DockerRuntimeEngine(fake,
                RuntimeConfiguration.productionDefaults(root).withCatalog(catalog));

        ExecutionResult result = engine.execute("workspace-1", new ExecutionRequest("python", "main.py", Map.of(),
                ExecutionPolicy.safeDefaults(), PythonDependencyRequest.fromManifest(forged)));

        assertEquals(ExecutionResult.Status.REJECTED, result.status());
        assertTrue(fake.spec == null);
    }

    @Test
    void installerFailureStopsExecutionAndStillCleansStagingDirectory() throws Exception {
        Path root = Files.createTempDirectory("reportai-runtime-");
        Path workspace = root.resolve("workspace-1");
        Files.createDirectories(workspace.resolve(".reportai-deps"));
        Files.writeString(workspace.resolve(".reportai-deps/stale.whl"), "stale");
        FakeClient fake = new FakeClient();
        fake.exitCode = 1;
        DockerRuntimeEngine engine = new DockerRuntimeEngine(fake, RuntimeConfiguration.productionDefaults(root));

        ExecutionResult result = engine.execute("workspace-1", new ExecutionRequest("python", "main.py", Map.of(),
                ExecutionPolicy.safeDefaults(), List.of("numpy==1.26.4")));

        assertEquals(ExecutionResult.Status.FAILED, result.status());
        assertEquals(1, fake.specs.size());
        assertTrue(!Files.exists(workspace.resolve(".reportai-deps")));
    }

    @Test
    void dependencyContainersShareAndRemoveAnEphemeralVolumeWhenClientSupportsIt() throws Exception {
        Path root = Files.createTempDirectory("reportai-runtime-");
        Files.createDirectories(root.resolve("workspace-1"));
        FakeClient fake = new FakeClient();
        fake.useVolume = true;
        DockerRuntimeEngine engine = new DockerRuntimeEngine(fake, RuntimeConfiguration.productionDefaults(root));

        ExecutionResult result = engine.execute("workspace-1", new ExecutionRequest("python", "main.py", Map.of(),
                ExecutionPolicy.safeDefaults(), List.of("numpy==1.26.4")));

        assertEquals(ExecutionResult.Status.SUCCEEDED, result.status());
        assertEquals(List.of("reportai-volume", "reportai-volume"), fake.containerVolumes);
        assertEquals("reportai-volume", fake.removedVolume);
    }

    @Test
    void lifecycleKeepsInstallerVolumeAcrossCheckpointBoundaryUntilExplicitCleanup() throws Exception {
        Path root = Files.createTempDirectory("reportai-runtime-lifecycle-");
        Path workspace = Files.createDirectories(root.resolve("workspace-1"));
        Files.createDirectories(workspace.resolve(".reportai-deps"));
        FakeClient fake = new FakeClient();
        fake.useVolume = true;
        DockerRuntimeEngine engine = new DockerRuntimeEngine(fake, RuntimeConfiguration.productionDefaults(root));
        ExecutionRequest request = new ExecutionRequest("python", "main.py", Map.of(),
                ExecutionPolicy.safeDefaults(), List.of("numpy==1.26.4"));

        RuntimeInstallResult installed = engine.install("workspace-1", request);
        assertTrue(installed.succeeded());
        assertEquals("reportai-volume", installed.dependencyVolume());
        assertEquals(1, fake.specs.size());

        ExecutionResult executed = engine.executeInstalled("workspace-1", request, installed.dependencyVolume());
        assertEquals(ExecutionResult.Status.SUCCEEDED, executed.status());
        assertEquals("reportai-volume", fake.containerVolumes.get(1));
        assertEquals(null, fake.removedVolume);

        engine.cleanup("workspace-1", installed.dependencyVolume());
        assertEquals("reportai-volume", fake.removedVolume);
        assertTrue(!Files.exists(workspace.resolve(".reportai-deps")));
    }

    private static final class FakeClient implements DockerSandboxClient {
        private SandboxContainerSpec spec;
        private final java.util.List<SandboxContainerSpec> specs = new java.util.ArrayList<>();
        private boolean killed;
        private boolean removed;
        private int exitCode;
        private boolean useVolume;
        private final java.util.List<String> containerVolumes = new java.util.ArrayList<>();
        private String removedVolume;

        @Override
        public String createContainer(SandboxContainerSpec specification) {
            spec = specification;
            specs.add(specification);
            return "fake-container";
        }

        @Override
        public String createEphemeralVolume(String purpose) {
            return useVolume ? "reportai-volume" : null;
        }

        @Override
        public String createContainer(SandboxContainerSpec specification, String dependencyVolume) {
            if (dependencyVolume != null) containerVolumes.add(dependencyVolume);
            return createContainer(specification);
        }

        @Override
        public void removeEphemeralVolume(String volumeName) {
            removedVolume = volumeName;
        }

        @Override
        public void startContainer(String containerId) {
        }

        @Override
        public SandboxExecution awaitAndCollect(String containerId, Duration timeout, long maxOutputBytes) {
            return new SandboxExecution(exitCode, false, false, "ok", "");
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
