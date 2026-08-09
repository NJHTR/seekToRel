package com.reportai.workflow;

import java.util.Optional;

import java.util.Map;

public interface WorkflowEngine {
    WorkflowInstance start(String definitionId, String taskId);

    WorkflowInstance resume(String workflowId, long expectedVersion);

    WorkflowInstance pause(String workflowId, long expectedVersion);

    WorkflowInstance cancel(String workflowId, long expectedVersion);

    WorkflowInstance complete(String workflowId, long expectedVersion);

    WorkflowInstance fail(String workflowId, long expectedVersion);

    /** Persists node/state progress while keeping the current lifecycle status. */
    WorkflowInstance checkpoint(String workflowId, long expectedVersion,
                                String currentNodeId, Map<String, Object> state);

    WorkflowInstance get(String workflowId);

    Optional<WorkflowInstance> findByTaskId(String taskId);
}
