package com.reportai.llm;

import java.util.List;
import java.util.Map;

public record ToolCallRequest(String model, List<ChatMessage> messages, List<ToolDefinition> tools,
                              int maxToolCalls, Map<String, Object> options,
                              LlmRequestBudget budget, String requestId) implements LlmRequest {
    public ToolCallRequest {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        if (tools == null || tools.isEmpty()) {
            throw new IllegalArgumentException("tools must not be empty");
        }
        if (maxToolCalls <= 0 || maxToolCalls > 64) {
            throw new IllegalArgumentException("maxToolCalls must be between 1 and 64");
        }
        if (messages.stream().anyMatch(java.util.Objects::isNull)
                || tools.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("messages and tools must not contain null values");
        }
        long distinctToolNames = tools.stream().map(ToolDefinition::name).distinct().count();
        if (distinctToolNames != tools.size()) {
            throw new IllegalArgumentException("tool names must be unique");
        }
        model = model == null || model.isBlank() ? null : model.trim();
        messages = List.copyOf(messages);
        tools = List.copyOf(tools);
        options = options == null ? Map.of() : Map.copyOf(options);
        budget = budget == null ? LlmRequestBudget.safeDefaults() : budget;
        requestId = requestId == null ? "" : requestId.trim();
    }

    @Override
    public int estimatedInputTokens() {
        return TokenEstimator.approximate(messages.stream().map(ChatMessage::content).toList());
    }
}
