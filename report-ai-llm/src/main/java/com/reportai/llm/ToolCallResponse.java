package com.reportai.llm;

import java.util.List;
import java.util.Map;

public record ToolCallResponse(String providerId, String model, String content, List<ToolCall> toolCalls,
                               String finishReason, TokenUsage usage, Map<String, Object> metadata) {
    public ToolCallResponse {
        providerId = providerId == null ? "" : providerId.trim();
        model = model == null ? "" : model.trim();
        content = content == null ? "" : content;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        finishReason = finishReason == null ? "" : finishReason.trim();
        usage = usage == null ? TokenUsage.empty() : usage;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
