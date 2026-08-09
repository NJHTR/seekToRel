package com.reportai.llm;

import java.util.List;

public record EmbeddingRequest(String model, List<String> inputs, LlmRequestBudget budget, String requestId)
        implements LlmRequest {
    public EmbeddingRequest {
        if (inputs == null || inputs.isEmpty() || inputs.size() > 128) {
            throw new IllegalArgumentException("inputs must contain between 1 and 128 items");
        }
        if (inputs.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("inputs must not contain blank values");
        }
        if (inputs.stream().anyMatch(value -> value.length() > 1_000_000)) {
            throw new IllegalArgumentException("embedding input is too long");
        }
        model = model == null || model.isBlank() ? null : model.trim();
        inputs = List.copyOf(inputs);
        budget = budget == null ? LlmRequestBudget.safeDefaults() : budget;
        requestId = requestId == null ? "" : requestId.trim();
    }

    @Override
    public int estimatedInputTokens() {
        return TokenEstimator.approximate(inputs);
    }
}
