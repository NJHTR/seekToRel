package com.reportai.application.task;

import com.reportai.agent.AgentContext;
import com.reportai.agent.AgentResult;
import com.reportai.common.tenant.TenantId;
import com.reportai.runtime.DependencySpec;
import com.reportai.runtime.EnvironmentManifest;
import com.reportai.runtime.EnvironmentManifestValidator;
import com.reportai.runtime.InputRequirement;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentPreflightAgentTest {
    private static final String IMAGE = "python@sha256:57cd7c3a7a273101a6485ba99423ee568157882804b1124b4dd04266317710de";

    @Test
    void persistsSuccessfulPreflightAndManifestDigest() throws Exception {
        Path root = Files.createTempDirectory("reportai-preflight-agent-");
        Files.createDirectories(root.resolve("task-1"));
        EnvironmentManifest manifest = new EnvironmentManifest(1, "python", "main.py",
                List.of(new DependencySpec("numpy", "1.26.4", "0".repeat(64))), List.of(), IMAGE, "scientific");
        EnvironmentPreflightAgent agent = new EnvironmentPreflightAgent(new EnvironmentManifestValidator(), root,
                "task-1", value -> (EnvironmentManifest) value);

        AgentResult result = agent.execute(new AgentContext("task-1", new TenantId("tenant-1"),
                Map.of("environmentManifest", manifest), null, null));

        assertEquals(AgentResult.Status.COMPLETED, result.status());
        assertEquals(manifest.manifestDigest(), result.outputs().get("environmentManifestDigest"));
    }

    @Test
    void failsBeforeRuntimeWhenRequiredInputIsMissing() throws Exception {
        Path root = Files.createTempDirectory("reportai-preflight-agent-");
        Files.createDirectories(root.resolve("task-1"));
        EnvironmentManifest manifest = new EnvironmentManifest(1, "python", "main.py", List.of(),
                List.of(new InputRequirement("data/input.csv", 1024)), IMAGE, "default");
        EnvironmentPreflightAgent agent = new EnvironmentPreflightAgent(new EnvironmentManifestValidator(), root,
                "task-1", value -> (EnvironmentManifest) value);

        AgentResult result = agent.execute(new AgentContext("task-1", new TenantId("tenant-1"),
                Map.of("environmentManifest", manifest), null, null));

        assertEquals(AgentResult.Status.FAILED, result.status());
        assertTrue(result.message().startsWith("ENV_INPUT_MISSING"));
    }

    @Test
    void keepsLegacyNoDependencyExecutionCompatibleButBlocksUnresolvedDependencies() {
        EnvironmentPreflightAgent agent = new EnvironmentPreflightAgent(new EnvironmentManifestValidator(),
                Path.of("."), "task-1", value -> (EnvironmentManifest) value);
        AgentResult legacy = agent.execute(new AgentContext("task-1", new TenantId("tenant-1"),
                Map.of("runtimeRequest", Map.of("entryPoint", "main.py")), null, null));
        assertEquals(AgentResult.Status.COMPLETED, legacy.status());

        AgentResult unresolved = agent.execute(new AgentContext("task-1", new TenantId("tenant-1"),
                Map.of("runtimeRequest", Map.of("entryPoint", "main.py", "dependencies", List.of("numpy==1.26.4"))),
                null, null));
        assertEquals(AgentResult.Status.FAILED, unresolved.status());
        assertTrue(unresolved.message().startsWith("ENV_MANIFEST_REQUIRED"));
    }

    @Test
    void blocksScientificTorchExecutionWhenNoRealDatasetIsDeclared() throws Exception {
        Path root = Files.createTempDirectory("reportai-preflight-agent-");
        Files.createDirectories(root.resolve("task-1"));
        EnvironmentPreflightAgent agent = new EnvironmentPreflightAgent(new EnvironmentManifestValidator(),
                root, "task-1", value -> (EnvironmentManifest) value);
        EnvironmentManifest manifest = new EnvironmentManifest(1, "python", "main.py",
                List.of(new DependencySpec("torch", "2.5.1+cpu", "0".repeat(64))), List.of(), IMAGE,
                "scientific");

        AgentResult result = agent.execute(new AgentContext("task-1", new TenantId("tenant-1"),
                Map.of("environmentManifest", manifest), null, null));

        assertEquals(AgentResult.Status.FAILED, result.status());
        assertTrue(result.message().startsWith("ENV_PYTHON_INPUT_MISSING"), result.message());
        assertEquals("PYTHON_INPUT_MISSING", result.outputs().get("toolErrorCode"));
    }
}
