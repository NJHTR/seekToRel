package com.reportai.llm;

import java.time.Duration;

/** Caller requested limits. Providers must never exceed these values. */
public record LlmRequestBudget(int maxInputTokens, int maxOutputTokens, Duration timeout) {
    private static final int DEFAULT_INPUT_TOKENS = 32_000;
    private static final int DEFAULT_OUTPUT_TOKENS = 4_096;
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(180);

    public LlmRequestBudget {
        if (maxInputTokens <= 0 || maxInputTokens > 1_000_000) {
            throw new IllegalArgumentException("maxInputTokens must be between 1 and 1000000");
        }
        if (maxOutputTokens <= 0 || maxOutputTokens > 100_000) {
            throw new IllegalArgumentException("maxOutputTokens must be between 1 and 100000");
        }
        if (timeout == null || timeout.compareTo(Duration.ofMillis(1)) < 0
                || timeout.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalArgumentException("timeout must be between 1ms and 10m");
        }
    }

    public static LlmRequestBudget safeDefaults() {
        return new LlmRequestBudget(DEFAULT_INPUT_TOKENS, DEFAULT_OUTPUT_TOKENS, DEFAULT_TIMEOUT);
    }
}
