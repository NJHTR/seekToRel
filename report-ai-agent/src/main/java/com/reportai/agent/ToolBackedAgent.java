package com.reportai.agent;

import com.reportai.tool.Tool;
import com.reportai.tool.ToolContext;
import com.reportai.tool.ToolInput;
import com.reportai.tool.ToolRegistry;
import com.reportai.tool.ToolResult;

import java.util.Map;

/** Agent adapter that delegates every side effect to a registered Tool. */
public final class ToolBackedAgent implements Agent {
    private final String type;
    private final String toolName;
    private final String inputStateKey;
    private final String outputStateKey;

    public ToolBackedAgent(String type, String toolName, String inputStateKey, String outputStateKey) {
        this.type = require(type, "type");
        this.toolName = require(toolName, "toolName");
        this.inputStateKey = require(inputStateKey, "inputStateKey");
        this.outputStateKey = require(outputStateKey, "outputStateKey");
    }

    @Override
    public String type() {
        return type;
    }

    @Override
    public AgentResult execute(AgentContext context) {
        if (context == null || context.toolRegistry() == null || context.tenantId() == null) {
            return new AgentResult(AgentResult.Status.FAILED, Map.of(), "tool execution context is incomplete");
        }
        Object arguments = context.state().get(inputStateKey);
        if (!(arguments instanceof Map<?, ?> raw)) {
            return new AgentResult(AgentResult.Status.FAILED, Map.of(), "tool input must be an object: " + inputStateKey);
        }
        Map<String, Object> safe = raw.entrySet().stream().filter(entry -> entry.getKey() instanceof String)
                .collect(java.util.stream.Collectors.toUnmodifiableMap(entry -> (String) entry.getKey(), Map.Entry::getValue,
                        (first, ignored) -> first));
        ToolRegistry registry = context.toolRegistry();
        Tool tool = registry.find(toolName).orElse(null);
        if (tool == null) {
            return new AgentResult(AgentResult.Status.FAILED, Map.of(), "tool is not registered: " + toolName);
        }
        ToolResult result = tool.execute(new ToolContext(context.taskId(), context.tenantId(),
                String.valueOf(context.state().getOrDefault("workspaceId", ""))), new ToolInput(safe));
        if (result == null || result.status() == ToolResult.Status.FAILED) {
            if (result == null) {
                return new AgentResult(AgentResult.Status.FAILED, Map.of(), "tool returned no result");
            }
            Object errorCode = result.outputs().get("errorCode");
            Map<String, Object> outputs = safeFailureOutputs(result == null ? Map.of() : result.outputs(), errorCode);
            return new AgentResult(AgentResult.Status.FAILED, outputs, result.publicMessage());
        }
        if (result.status() == ToolResult.Status.REJECTED) {
            Object errorCode = result.outputs().get("errorCode");
            Map<String, Object> outputs = errorCode == null ? Map.of()
                    : Map.of("toolErrorCode", String.valueOf(errorCode));
            return new AgentResult(AgentResult.Status.FAILED, outputs, result.publicMessage());
        }
        return new AgentResult(AgentResult.Status.COMPLETED, Map.of(outputStateKey, result.outputs()),
                result.publicMessage());
    }

    private static Map<String, Object> safeFailureOutputs(Map<String, Object> values, Object errorCode) {
        Map<String, Object> outputs = new java.util.LinkedHashMap<>();
        if (errorCode != null) outputs.put("toolErrorCode", String.valueOf(errorCode));
        Object exitCode = values.get("exitCode");
        if (exitCode instanceof Number number) {
            long normalized = number.longValue();
            if (normalized == number.doubleValue() && normalized >= -1 && normalized <= 255) {
                outputs.put("toolExitCode", normalized);
            }
        }
        String stderr = diagnostic(values.get("stderr"));
        if (stderr != null) outputs.put("toolStderrSummary", stderr);
        // Lifecycle control flags are deliberately allow-listed; arbitrary
        // tool output (source, logs, or credentials) must not be copied into a
        // failure checkpoint.
        for (String key : java.util.List.of("rewindToInstaller", "rewindToCoder", "cleanupPerformed")) {
            Object value = values.get(key);
            if (value instanceof Boolean) outputs.put(key, value);
        }
        return Map.copyOf(outputs);
    }

    /**
     * Keeps a small, redacted diagnostic in the durable checkpoint. Full
     * process output remains an artifact owned by the runtime boundary; this
     * summary is only for explaining a deterministic failure in the UI.
     */
    private static String diagnostic(Object raw) {
        if (!(raw instanceof String value) || value.isBlank()) return null;
        String normalized = value.replaceAll("[\\p{Cc}&&[^\\r\\n\\t]]", "")
                .replaceAll("[\\r\\n\\t]+", " ").trim();
        normalized = normalized.replaceAll("(?i)(authorization|bearer|api[-_ ]?key|password|passwd|secret|token)"
                + "\\s*[:=]\\s*\\S+", "$1=[REDACTED]");
        normalized = normalized.replaceAll("(?i)\\bsk-[a-z0-9_-]{8,}\\b", "[REDACTED]");
        if (normalized.length() > 512) normalized = normalized.substring(0, 512);
        return normalized.isBlank() ? null : normalized;
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value.trim();
    }
}
