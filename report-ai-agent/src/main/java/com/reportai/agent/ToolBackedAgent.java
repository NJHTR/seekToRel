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
            return new AgentResult(AgentResult.Status.FAILED, Map.of(),
                    result == null ? "tool returned no result" : result.publicMessage());
        }
        if (result.status() == ToolResult.Status.REJECTED) {
            return new AgentResult(AgentResult.Status.FAILED, Map.of(), result.publicMessage());
        }
        return new AgentResult(AgentResult.Status.COMPLETED, Map.of(outputStateKey, result.outputs()),
                result.publicMessage());
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value.trim();
    }
}
