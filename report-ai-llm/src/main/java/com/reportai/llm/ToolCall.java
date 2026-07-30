package com.reportai.llm;

import java.util.Map;

public record ToolCall(String id, String name, Map<String, Object> arguments) {
    public ToolCall {
        if (id == null || id.isBlank() || id.length() > 128) {
            throw new IllegalArgumentException("tool call id is invalid");
        }
        if (name == null || !name.matches("[A-Za-z][A-Za-z0-9_.-]{0,63}")) {
            throw new IllegalArgumentException("tool name is invalid");
        }
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
