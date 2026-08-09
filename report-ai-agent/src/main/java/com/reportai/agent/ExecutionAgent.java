package com.reportai.agent;

/** Runs the already-installed program in the isolated execution container. */
public final class ExecutionAgent implements Agent {
    public static final String TYPE = "execution";
    private final ToolBackedAgent delegate = new ToolBackedAgent(TYPE, "python.execute",
            "runtimeRequest", "execution");

    @Override
    public String type() { return TYPE; }

    @Override
    public AgentResult execute(AgentContext context) { return delegate.execute(context); }
}
