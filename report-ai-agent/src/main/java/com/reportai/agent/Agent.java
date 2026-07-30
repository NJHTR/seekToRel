package com.reportai.agent;

public interface Agent {
    String type();

    AgentResult execute(AgentContext context);
}
