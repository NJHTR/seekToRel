package com.reportai.agent;

/** Durable status of one resumable agent pipeline. */
public enum AgentPipelineStatus {
    RUNNING,
    WAITING,
    COMPLETED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
