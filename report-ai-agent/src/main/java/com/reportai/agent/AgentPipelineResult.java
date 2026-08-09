package com.reportai.agent;

/** Result returned after one bounded, durable pipeline step. */
public record AgentPipelineResult(AgentPipelineStatus status,
                                  AgentPipelineCheckpoint checkpoint,
                                  AgentResult agentResult) {
    public AgentPipelineResult {
        if (status == null || checkpoint == null || agentResult == null) {
            throw new IllegalArgumentException("pipeline result fields are required");
        }
    }

    public boolean terminal() {
        return status.isTerminal();
    }
}
