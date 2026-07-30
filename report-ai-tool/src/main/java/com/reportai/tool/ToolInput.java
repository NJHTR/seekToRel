package com.reportai.tool;

import java.util.Map;

public record ToolInput(Map<String, Object> arguments) {
    public ToolInput {
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
