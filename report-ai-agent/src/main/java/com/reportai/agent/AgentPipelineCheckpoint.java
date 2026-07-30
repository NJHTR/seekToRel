package com.reportai.agent;

import com.reportai.common.tenant.TenantId;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Durable pipeline cursor. The LLM and tool instances are deliberately not part
 * of this object; they are runtime dependencies supplied again when a worker
 * resumes a task after a restart.
 */
public record AgentPipelineCheckpoint(
        String pipelineId,
        String taskId,
        TenantId tenantId,
        List<String> agentTypes,
        int nextAgentIndex,
        AgentPipelineStatus status,
        Map<String, Object> state,
        String message,
        long version,
        Instant updatedAt) {

    public AgentPipelineCheckpoint {
        if (pipelineId == null || pipelineId.isBlank() || taskId == null || taskId.isBlank()
                || tenantId == null || agentTypes == null || agentTypes.isEmpty()
                || agentTypes.size() > 256 || agentTypes.stream().anyMatch(type -> type == null || type.isBlank())) {
            throw new IllegalArgumentException("invalid agent pipeline identity");
        }
        if (nextAgentIndex < 0 || nextAgentIndex > agentTypes.size()) {
            throw new IllegalArgumentException("agent cursor is outside pipeline bounds");
        }
        if (status == null || version < 0 || updatedAt == null) {
            throw new IllegalArgumentException("invalid agent pipeline checkpoint");
        }
        agentTypes = agentTypes.stream().map(String::trim).toList();
        state = state == null ? Map.of() : Map.copyOf(state);
        message = message == null ? "" : message;
        if (message.length() > 16_384) {
            throw new IllegalArgumentException("agent pipeline message exceeds limit");
        }
    }

    public boolean isTerminal() {
        return status.isTerminal();
    }
}
