package com.reportai.agent;

import com.reportai.tool.PythonRunTool;

/**
 * Executes a prepared runtime request through the Tool Registry.  The agent has no
 * process, filesystem or Docker access of its own; the registered tool remains the
 * only side-effect boundary.
 */
public final class RuntimeAgent implements Agent {
    public static final String TYPE = "runtime";
    public static final String DEFAULT_TOOL_NAME = PythonRunTool.NAME;
    public static final String DEFAULT_INPUT_KEY = "runtimeRequest";
    public static final String DEFAULT_OUTPUT_KEY = "execution";

    private final ToolBackedAgent delegate;

    public RuntimeAgent() {
        this(DEFAULT_TOOL_NAME, DEFAULT_INPUT_KEY, DEFAULT_OUTPUT_KEY);
    }

    public RuntimeAgent(String toolName, String inputKey, String outputKey) {
        this.delegate = new ToolBackedAgent(TYPE, require(toolName, "toolName"),
                require(inputKey, "inputKey"), require(outputKey, "outputKey"));
    }

    @Override
    public String type() {
        return TYPE;
    }

    @Override
    public AgentResult execute(AgentContext context) {
        return delegate.execute(context);
    }

    private static String require(String value, String field) {
        if (value == null || value.isBlank() || value.length() > 128) {
            throw new IllegalArgumentException(field + " is invalid");
        }
        return value.trim();
    }
}
