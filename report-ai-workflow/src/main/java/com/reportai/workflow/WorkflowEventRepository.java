package com.reportai.workflow;

import java.util.List;

/** Durable append-only workflow event boundary. */
public interface WorkflowEventRepository {
    void append(WorkflowEvent event);

    List<WorkflowEvent> list(String workflowId, long afterVersion, int limit);
}
