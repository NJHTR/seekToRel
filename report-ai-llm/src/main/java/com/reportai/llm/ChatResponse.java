package com.reportai.llm;

import java.util.Map;

public record ChatResponse(String providerId, String model, String content, String finishReason,
                           TokenUsage usage, Map<String, Object> metadata) {
    public ChatResponse(String providerId, String model, String content, String finishReason) {
        this(providerId, model, content, finishReason, TokenUsage.empty(), Map.of());
    }

    public ChatResponse {
        providerId = providerId == null ? "" : providerId.trim();
        model = model == null ? "" : model.trim();
        content = content == null ? "" : content;
        finishReason = finishReason == null ? "" : finishReason.trim();
        usage = usage == null ? TokenUsage.empty() : usage;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
