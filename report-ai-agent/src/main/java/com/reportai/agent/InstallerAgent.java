package com.reportai.agent;

/** Runs the dependency installer container and persists its opaque volume handle. */
public final class InstallerAgent implements Agent {
    public static final String TYPE = "installer";
    private final ToolBackedAgent delegate = new ToolBackedAgent(TYPE, "python.install",
            "runtimeRequest", "installation");

    @Override
    public String type() { return TYPE; }

    @Override
    public AgentResult execute(AgentContext context) { return delegate.execute(context); }
}
