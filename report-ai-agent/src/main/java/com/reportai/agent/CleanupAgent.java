package com.reportai.agent;

/** Removes the task-scoped dependency volume and installer bookkeeping. */
public final class CleanupAgent implements Agent {
    public static final String TYPE = "cleanup";
    private final ToolBackedAgent delegate = new ToolBackedAgent(TYPE, "python.cleanup",
            "cleanupRequest", "cleanup");

    @Override
    public String type() { return TYPE; }

    @Override
    public AgentResult execute(AgentContext context) { return delegate.execute(context); }
}
