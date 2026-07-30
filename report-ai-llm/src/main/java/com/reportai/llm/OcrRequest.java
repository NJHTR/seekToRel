package com.reportai.llm;

import java.util.List;

public record OcrRequest(String model, String languageHint, List<OcrInput> inputs,
                         LlmRequestBudget budget, String requestId) implements LlmRequest {
    public OcrRequest {
        if (inputs == null || inputs.isEmpty() || inputs.size() > 64) {
            throw new IllegalArgumentException("inputs must contain between 1 and 64 items");
        }
        if (inputs.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("inputs must not contain null values");
        }
        languageHint = languageHint == null ? "" : languageHint.trim();
        if (languageHint.length() > 32 || !languageHint.matches("[A-Za-z0-9_-]*")) {
            throw new IllegalArgumentException("languageHint is invalid");
        }
        model = model == null || model.isBlank() ? null : model.trim();
        inputs = List.copyOf(inputs);
        budget = budget == null ? LlmRequestBudget.safeDefaults() : budget;
        requestId = requestId == null ? "" : requestId.trim();
    }

    @Override
    public int estimatedInputTokens() {
        return 0;
    }
}
