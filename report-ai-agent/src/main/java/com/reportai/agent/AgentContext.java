package com.reportai.agent;

import com.reportai.common.tenant.TenantId;
import com.reportai.llm.LlmProvider;
import com.reportai.llm.ChatAttachmentResolver;
import com.reportai.tool.ToolRegistry;

import java.util.Map;
import java.util.Set;

public record AgentContext(
        String taskId,
        TenantId tenantId,
        Map<String, Object> state,
        LlmProvider llmProvider,
        ToolRegistry toolRegistry,
        Map<String, Object> llmOptions,
        ChatAttachmentResolver attachmentResolver,
        Set<String> authoritativeStateKeys) {

    public AgentContext(String taskId, TenantId tenantId, Map<String, Object> state,
                        LlmProvider llmProvider, ToolRegistry toolRegistry) {
        this(taskId, tenantId, state, llmProvider, toolRegistry, Map.of(), ChatAttachmentResolver.none(), Set.of());
    }

    public AgentContext(String taskId, TenantId tenantId, Map<String, Object> state,
                        LlmProvider llmProvider, ToolRegistry toolRegistry,
                        Map<String, Object> llmOptions) {
        this(taskId, tenantId, state, llmProvider, toolRegistry, llmOptions,
                ChatAttachmentResolver.none(), Set.of());
    }

    public AgentContext(String taskId, TenantId tenantId, Map<String, Object> state,
                        LlmProvider llmProvider, ToolRegistry toolRegistry,
                        Map<String, Object> llmOptions, ChatAttachmentResolver attachmentResolver) {
        this(taskId, tenantId, state, llmProvider, toolRegistry, llmOptions,
                attachmentResolver, Set.of());
    }

    public AgentContext {
        state = state == null ? Map.of() : Map.copyOf(state);
        llmOptions = llmOptions == null ? Map.of() : Map.copyOf(llmOptions);
        attachmentResolver = attachmentResolver == null ? ChatAttachmentResolver.none() : attachmentResolver;
        authoritativeStateKeys = authoritativeStateKeys == null ? Set.of() : authoritativeStateKeys.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }
}
