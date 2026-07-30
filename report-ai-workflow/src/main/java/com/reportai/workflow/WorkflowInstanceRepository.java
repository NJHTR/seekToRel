package com.reportai.workflow;

import java.util.Optional;

/** Persistence port. Implementations must enforce compare-and-set on version. */
public interface WorkflowInstanceRepository {
    Optional<WorkflowInstance> findById(String workflowId);

    Optional<WorkflowInstance> findByTaskId(String taskId);

    WorkflowInstance create(WorkflowInstance instance);

    WorkflowInstance update(WorkflowInstance instance, long expectedVersion);
}
