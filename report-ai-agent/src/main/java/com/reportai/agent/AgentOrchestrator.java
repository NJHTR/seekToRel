package com.reportai.agent;

import java.util.List;

public interface AgentOrchestrator {
    AgentResult execute(AgentContext context, List<? extends Agent> agents);
}
