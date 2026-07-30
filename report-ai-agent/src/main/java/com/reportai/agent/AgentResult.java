package com.reportai.agent;

import java.util.Map;

public record AgentResult(Status status, Map<String, Object> outputs, String message) {
    public enum Status { COMPLETED, WAITING, FAILED }

    public AgentResult {
        outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
    }
}
