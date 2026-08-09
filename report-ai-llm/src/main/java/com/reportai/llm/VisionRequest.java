package com.reportai.llm;

import java.util.List;

public record VisionRequest(String model, String instruction, List<VisionInput> inputs,
                            LlmRequestBudget budget, String requestId) implements LlmRequest {
    public VisionRequest {
        if (inputs == null || inputs.isEmpty() || inputs.size() > 64) {
            throw new IllegalArgumentException("inputs must contain between 1 and 64 items");
        }
        if (inputs.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("inputs must not contain null values");
        }
        instruction = instruction == null ? "" : instruction;
        if (instruction.length() > 1_000_000) {
            throw new IllegalArgumentException("instruction is too long");
        }
        model = model == null || model.isBlank() ? null : model.trim();
        inputs = List.copyOf(inputs);
        budget = budget == null ? LlmRequestBudget.safeDefaults() : budget;
        requestId = requestId == null ? "" : requestId.trim();
    }

    @Override
    public int estimatedInputTokens() {
        return TokenEstimator.approximate(instruction);
    }
}
