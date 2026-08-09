package com.reportai.application.task;

import com.reportai.agent.Agent;
import com.reportai.agent.AgentContext;
import com.reportai.agent.AgentResult;
import com.reportai.tool.Tool;
import com.reportai.tool.ToolContext;
import com.reportai.tool.ToolInput;
import com.reportai.tool.ToolResult;

import java.util.Map;

/**
 * Commits sandbox files and logs to object storage before Cleanup is allowed to
 * advance.  This is intentionally an application agent: the runtime only
 * exposes bounded workspace-relative names, while this layer owns tenant
 * storage and integrity metadata.
 */
final class ExecutionArtifactAgent implements Agent {
    static final String TYPE = "artifact-persistence";

    private final ExecutionArtifactPersistenceService persistence;

    ExecutionArtifactAgent(ExecutionArtifactPersistenceService persistence) {
        this.persistence = persistence;
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public AgentResult execute(AgentContext context) {
        if (context == null || context.tenantId() == null) {
            return failed("ARTIFACT_CONTEXT_INVALID: artifact context is incomplete");
        }
        Object raw = context.state().get("execution");
        if (!(raw instanceof Map<?, ?> execution)) {
            return failed("ARTIFACT_EXECUTION_MISSING: execution output is missing");
        }
        if (persistence == null) {
            if (hasOutput(execution)) {
                return failed("ARTIFACT_STORAGE_UNAVAILABLE: object storage is required for sandbox outputs");
            }
            return new AgentResult(AgentResult.Status.COMPLETED,
                    Map.of("artifactReferences", java.util.List.of()),
                    "no sandbox artifacts to persist");
        }
        try {
            ExecutionArtifactPersistenceService.PersistedArtifacts result = persistence.persist(
                    context.tenantId(), context.taskId(),
                    String.valueOf(context.state().getOrDefault("workspaceId", "")), execution);
            return new AgentResult(AgentResult.Status.COMPLETED, result.state(),
                    "sandbox artifacts persisted");
        } catch (RuntimeException failure) {
            // Artifact persistence is after Execution but before the durable
            // Cleanup step.  If storage fails, clean the task-owned volume now
            // and ask the pipeline to replay Installer on the next delivery.
            boolean cleaned = cleanupAfterFailure(context);
            Map<String, Object> outputs = new java.util.LinkedHashMap<>();
            outputs.put("toolErrorCode", "ARTIFACT_PERSISTENCE_FAILED");
            outputs.put("cleanupPerformed", cleaned);
            if (cleaned) outputs.put("rewindToInstaller", true);
            return new AgentResult(AgentResult.Status.FAILED, Map.copyOf(outputs),
                    "ARTIFACT_PERSISTENCE_FAILED: " + bounded(failure.getMessage()));
        }
    }

    private static boolean cleanupAfterFailure(AgentContext context) {
        if (context.toolRegistry() == null) return false;
        Tool cleanup = context.toolRegistry().find("python.cleanup").orElse(null);
        if (cleanup == null) return false;
        Object raw = context.state().get("cleanupRequest");
        if (!(raw instanceof Map<?, ?> values)) return false;
        Map<String, Object> arguments = new java.util.LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key instanceof String name) arguments.put(name, value);
        });
        try {
            ToolResult result = cleanup.execute(
                    new ToolContext(context.taskId(), context.tenantId(),
                            String.valueOf(context.state().getOrDefault("workspaceId", ""))),
                    new ToolInput(Map.copyOf(arguments)));
            return result != null && result.status() == ToolResult.Status.SUCCEEDED;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    private static boolean hasOutput(Map<?, ?> execution) {
        Object keys = execution.get("artifactKeys");
        if (keys instanceof java.util.List<?> values && !values.isEmpty()) return true;
        return nonBlank(execution.get("stdout")) || nonBlank(execution.get("stderr"));
    }

    private static boolean nonBlank(Object value) {
        return value instanceof String text && !text.isBlank();
    }

    private static AgentResult failed(String message) {
        return new AgentResult(AgentResult.Status.FAILED,
                Map.of("toolErrorCode", "ARTIFACT_PERSISTENCE_FAILED"), message);
    }

    private static String bounded(String value) {
        if (value == null || value.isBlank()) return "artifact output could not be persisted";
        return value.length() <= 256 ? value : value.substring(0, 256);
    }
}
