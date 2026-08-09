package com.reportai.tool;

import java.util.Map;
import java.util.Objects;

public record ToolResult(Status status, Map<String, Object> outputs, String publicMessage) {
    public enum Status { SUCCEEDED, REJECTED, FAILED }

    public ToolResult {
        status = Objects.requireNonNull(status, "status");
        outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
        publicMessage = publicMessage == null ? "" : publicMessage;
    }
}
