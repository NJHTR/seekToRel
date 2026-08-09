package com.reportai.llm;

import java.time.Duration;

/** Server-side hard limits. A caller cannot raise these limits through request options. */
public record LlmRequestPolicy(int maxInputTokens, int maxOutputTokens, Duration maxTimeout,
                               int maxMessages, int maxToolCalls, int maxBatchSize,
                               int maxImageBytes) {
    public LlmRequestPolicy {
        if (maxInputTokens <= 0 || maxInputTokens > 1_000_000
                || maxOutputTokens <= 0 || maxOutputTokens > 100_000
                || maxMessages <= 0 || maxToolCalls <= 0 || maxBatchSize <= 0 || maxImageBytes <= 0) {
            throw new IllegalArgumentException("LLM policy limits must be positive and bounded");
        }
        if (maxTimeout == null || maxTimeout.compareTo(Duration.ofMillis(1)) < 0
                || maxTimeout.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalArgumentException("maxTimeout must be between 1ms and 10m");
        }
    }

    public static LlmRequestPolicy safeDefaults() {
        return new LlmRequestPolicy(32_000, 16_384, Duration.ofSeconds(180),
                128, 16, 64, 20 * 1024 * 1024);
    }
}
