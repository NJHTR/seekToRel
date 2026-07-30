package com.reportai.agent;

public record AgentExecutionPolicy(int maxSteps, int maxStateEntries, int maxOutputMessageLength) {
    public AgentExecutionPolicy {
        if (maxSteps <= 0 || maxSteps > 256 || maxStateEntries <= 0 || maxStateEntries > 10_000
                || maxOutputMessageLength <= 0 || maxOutputMessageLength > 1_000_000) {
            throw new IllegalArgumentException("agent execution policy is outside safe bounds");
        }
    }

    public static AgentExecutionPolicy safeDefaults() {
        return new AgentExecutionPolicy(32, 2_000, 16_384);
    }
}
