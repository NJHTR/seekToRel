package com.reportai.llm;

import java.util.List;
import java.util.Map;

public record ChatRequest(String model, List<ChatMessage> messages, Map<String, Object> options,
                          LlmRequestBudget budget, String requestId) implements LlmRequest {
    public ChatRequest(String model, List<ChatMessage> messages, Map<String, Object> options) {
        this(model, messages, options, LlmRequestBudget.safeDefaults(), "");
    }

    public ChatRequest {
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("messages must not be empty");
        }
        if (messages.size() > 1_024) {
            throw new IllegalArgumentException("messages exceed the maximum allowed count");
        }
        if (messages.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("messages must not contain null values");
        }
        model = model == null || model.isBlank() ? null : model.trim();
        messages = List.copyOf(messages);
        options = options == null ? Map.of() : Map.copyOf(options);
        budget = budget == null ? LlmRequestBudget.safeDefaults() : budget;
        requestId = requestId == null ? "" : requestId.trim();
    }

    @Override
    public int estimatedInputTokens() {
        return TokenEstimator.approximate(messages.stream().map(ChatMessage::content).toList());
    }
}
