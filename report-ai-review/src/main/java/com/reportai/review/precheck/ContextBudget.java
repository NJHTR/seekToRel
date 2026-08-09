package com.reportai.review.precheck;

public record ContextBudget(int maxChars, int maxTokens, int maxItems) {
    public ContextBudget {
        if (maxChars <= 0 || maxChars > 1_000_000 || maxTokens <= 0 || maxTokens > 250_000 || maxItems <= 0 || maxItems > 10_000)
            throw new IllegalArgumentException("context budget out of range");
    }
    public static ContextBudget defaults() { return new ContextBudget(40_000, 10_000, 128); }
}
