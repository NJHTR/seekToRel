package com.reportai.api.workflow;

import java.util.Map;

public record WorkflowResponse(
        String workflowId,
        String definitionId,
        int definitionVersion,
        String taskId,
        String currentNodeId,
        String status,
        Map<String, Object> state,
        long version) {
}
