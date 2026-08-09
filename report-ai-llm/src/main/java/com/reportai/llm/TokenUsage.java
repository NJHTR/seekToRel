package com.reportai.llm;

public record TokenUsage(int inputTokens, int outputTokens, int totalTokens) {
    public TokenUsage {
        if (inputTokens < 0 || outputTokens < 0 || totalTokens < 0) {
            throw new IllegalArgumentException("token usage must not be negative");
        }
        if (totalTokens != 0 && totalTokens != inputTokens + outputTokens) {
            throw new IllegalArgumentException("totalTokens must equal inputTokens + outputTokens");
        }
    }

    public static TokenUsage empty() {
        return new TokenUsage(0, 0, 0);
    }
}
