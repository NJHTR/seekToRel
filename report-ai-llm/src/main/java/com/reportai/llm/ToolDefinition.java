package com.reportai.llm;

import java.util.Map;

public record ToolDefinition(String name, String description, Map<String, Object> inputSchema) {
    public ToolDefinition {
        if (name == null || !name.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("tool name is invalid");
        }
        description = description == null ? "" : description;
        if (description.length() > 16_384) {
            throw new IllegalArgumentException("tool description is too long");
        }
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
    }
}
