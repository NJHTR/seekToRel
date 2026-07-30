package com.reportai.agent;

/**
 * Provider-neutral safety limits for assembling a conversation prompt.
 *
 * <p>The policy reserves output capacity before selecting history. This keeps
 * prompt assembly deterministic and prevents a long conversation from
 * consuming the entire model request budget.</p>
 */
public record ContextWindowPolicy(int maxInputTokens, int reservedOutputTokens,
                                  int maxMessages, int maxMessageChars,
                                  int maxSummaryChars) {
    private static final int MAX_INPUT_TOKENS = 1_000_000;
    private static final int MAX_MESSAGES = 1_000;
    private static final int MAX_MESSAGE_CHARS = 512 * 1024;
    private static final int MAX_SUMMARY_CHARS = 128 * 1024;

    public ContextWindowPolicy {
        if (maxInputTokens < 128 || maxInputTokens > MAX_INPUT_TOKENS) {
            throw new IllegalArgumentException("maxInputTokens is outside the permitted range");
        }
        if (reservedOutputTokens < 1 || reservedOutputTokens >= maxInputTokens) {
            throw new IllegalArgumentException("reservedOutputTokens is outside the permitted range");
        }
        if (maxMessages < 1 || maxMessages > MAX_MESSAGES) {
            throw new IllegalArgumentException("maxMessages is outside the permitted range");
        }
        if (maxMessageChars < 64 || maxMessageChars > MAX_MESSAGE_CHARS) {
            throw new IllegalArgumentException("maxMessageChars is outside the permitted range");
        }
        if (maxSummaryChars < 128 || maxSummaryChars > MAX_SUMMARY_CHARS) {
            throw new IllegalArgumentException("maxSummaryChars is outside the permitted range");
        }
    }

    public static ContextWindowPolicy safeDefaults() {
        return new ContextWindowPolicy(16_384, 2_048, 128, 32 * 1024, 16 * 1024);
    }

    public int historyTokenBudget() {
        return maxInputTokens - reservedOutputTokens;
    }
}
