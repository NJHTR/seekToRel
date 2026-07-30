package com.reportai.agent;

import com.reportai.common.tenant.TenantId;
import com.reportai.llm.LlmProvider;
import com.reportai.tool.ToolRegistry;

import java.util.Map;

public record AgentContext(
        String taskId,
        TenantId tenantId,
        Map<String, Object> state,
        LlmProvider llmProvider,
        ToolRegistry toolRegistry) {

    public AgentContext {
        state = state == null ? Map.of() : Map.copyOf(state);
    }
}
