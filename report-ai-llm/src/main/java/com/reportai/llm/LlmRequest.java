package com.reportai.llm;

public interface LlmRequest {
    String model();

    LlmRequestBudget budget();

    String requestId();

    int estimatedInputTokens();
}
